"""发布生成好的审核事件到 RabbitMQ，并记录每条消息的处理结果。"""

from __future__ import annotations

import csv
import json
import time
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any
from uuid import uuid4

from annotation_agent.app.settings import AgentSettings, load_settings
from annotation_agent.infrastructure.persistence.mysql.connection import MySQLConnectionFactory


TERMINAL_STATUSES = {"completed", "failed", "max_retries_exceeded"}
DEFAULT_EXCHANGE = "ai.review.exchange"
DEFAULT_ROUTING_KEY = "ai.review"


@dataclass(frozen=True)
class PressureTestConfig:
    """RabbitMQ 压测运行参数。"""

    input_path: Path
    report_dir: Path
    exchange: str = DEFAULT_EXCHANGE
    routing_key: str = DEFAULT_ROUTING_KEY
    queue_name: str | None = None
    declare_topology: bool = False
    poll_interval_seconds: float = 1.0
    timeout_seconds: float = 180.0
    publish_interval_seconds: float = 0.0
    limit: int | None = None
    run_id: str = ""


def run_pressure_test(config: PressureTestConfig, settings: AgentSettings | None = None) -> dict[str, Any]:
    """执行一次完整压测：发消息、轮询 MySQL 结果、输出报告。"""
    settings = settings or load_settings()
    source_messages = _load_messages(config.input_path)
    if config.limit is not None:
        source_messages = source_messages[: max(0, config.limit)]
    if not source_messages:
        raise ValueError(f"no MQ messages found in {config.input_path}")

    run_id = config.run_id or _default_run_id()
    # 每次运行都改写任务标识，避免重复压测覆盖旧记录。
    messages = [prepare_run_message(message, run_id, index) for index, message in enumerate(source_messages, start=1)]

    publish_started_at = time.perf_counter()
    _publish_messages(messages, settings, config)
    publish_elapsed_ms = round((time.perf_counter() - publish_started_at) * 1000, 2)

    tracked_records = {
        message["taskItemId"]: build_initial_record(message, config.input_path.name)
        for message in messages
    }
    poll_started_at = time.perf_counter()
    terminal_records = _poll_until_finished(tracked_records, settings, config)
    poll_elapsed_ms = round((time.perf_counter() - poll_started_at) * 1000, 2)

    report = {
        "runId": run_id,
        "inputPath": str(config.input_path),
        "exchange": config.exchange,
        "routingKey": config.routing_key,
        "queueName": config.queue_name or settings.rabbitmq.queue_name,
        "messageCount": len(messages),
        "publishedAt": iso_utc_now(),
        "publishElapsedMs": publish_elapsed_ms,
        "pollElapsedMs": poll_elapsed_ms,
        "summary": summarize_records(list(terminal_records.values())),
        "records": list(terminal_records.values()),
    }
    write_pressure_report(config.report_dir, run_id, report)
    return report


def declare_topology(config: PressureTestConfig, settings: AgentSettings | None = None) -> dict[str, str]:
    """声明一套隔离的直连交换机/队列/路由键，供压测 worker 使用。"""
    settings = settings or load_settings()
    try:
        import pika
    except ImportError as exc:  # pragma: no cover - optional runtime dependency
        raise RuntimeError("Missing pika dependency. Run pip install -r requirements.txt") from exc

    credentials = pika.PlainCredentials(settings.rabbitmq.username, settings.rabbitmq.password)
    parameters = pika.ConnectionParameters(
        host=settings.rabbitmq.host,
        port=settings.rabbitmq.port,
        virtual_host=settings.rabbitmq.vhost,
        credentials=credentials,
        heartbeat=settings.rabbitmq.heartbeat,
        blocked_connection_timeout=settings.rabbitmq.blocked_connection_timeout,
    )
    queue_name = config.queue_name or settings.rabbitmq.queue_name
    connection = pika.BlockingConnection(parameters)
    try:
        channel = connection.channel()
        _declare_topology(channel, config, settings)
    finally:
        connection.close()
    return {
        "exchange": config.exchange,
        "routingKey": config.routing_key,
        "queueName": queue_name,
    }


def prepare_run_message(source_message: dict[str, Any], run_id: str, index: int) -> dict[str, Any]:
    """复制源消息，并把 task 标识改成当前运行唯一值。"""
    message = json.loads(json.dumps(source_message, ensure_ascii=False))
    original_task_item_id = str(message.get("taskItemId") or f"source-{index}")
    original_task_id = str(message.get("taskId") or "dataset")
    unique_task_item_id = f"{original_task_item_id}--{run_id}--{index:04d}"
    message["taskItemId"] = unique_task_item_id
    message["taskId"] = f"{original_task_id}--{run_id}"
    message["pressureTestMeta"] = {
        "runId": run_id,
        "sequence": index,
        "sourceTaskId": original_task_id,
        "sourceTaskItemId": original_task_item_id,
        "publishedAt": iso_utc_now(),
    }
    return message


def build_initial_record(message: dict[str, Any], source_file_name: str) -> dict[str, Any]:
    """在轮询 MySQL 前，为每条消息创建一条本地跟踪记录。"""
    meta = message.get("pressureTestMeta") if isinstance(message.get("pressureTestMeta"), dict) else {}
    published_at = str(meta.get("publishedAt") or iso_utc_now())
    return {
        "runId": str(meta.get("runId") or ""),
        "sequence": int(meta.get("sequence") or 0),
        "sourceFile": source_file_name,
        "sourceTaskId": str(meta.get("sourceTaskId") or ""),
        "sourceTaskItemId": str(meta.get("sourceTaskItemId") or ""),
        "taskId": str(message.get("taskId") or ""),
        "taskItemId": str(message.get("taskItemId") or ""),
        "publishedAt": published_at,
        "firstSeenAt": "",
        "completedAt": "",
        "latencyMs": None,
        "status": "published",
        "auditStatus": "",
        "attemptCount": 0,
        "success": False,
        "summary": "",
        "lastError": "",
        "confidence": 0,
        "weightedScore": 0,
        "timedOut": False,
    }


def summarize_records(records: list[dict[str, Any]]) -> dict[str, Any]:
    """汇总压测结果，生成成功数、失败数、耗时分布等摘要。"""
    success_count = sum(1 for record in records if record.get("status") == "completed")
    failed_count = sum(1 for record in records if record.get("status") == "failed")
    max_retry_count = sum(1 for record in records if record.get("status") == "max_retries_exceeded")
    timed_out_count = sum(1 for record in records if record.get("timedOut"))
    latencies = [float(record["latencyMs"]) for record in records if isinstance(record.get("latencyMs"), (int, float))]
    return {
        "successCount": success_count,
        "failedCount": failed_count,
        "maxRetriesExceededCount": max_retry_count,
        "timedOutCount": timed_out_count,
        "minLatencyMs": min(latencies) if latencies else None,
        "maxLatencyMs": max(latencies) if latencies else None,
        "avgLatencyMs": round(sum(latencies) / len(latencies), 2) if latencies else None,
    }


def write_pressure_report(report_dir: Path, run_id: str, report: dict[str, Any]) -> None:
    """把压测结果同时落成 JSON 和 CSV，便于程序读取和人工查看。"""
    report_dir.mkdir(parents=True, exist_ok=True)
    json_path = report_dir / f"{run_id}.json"
    csv_path = report_dir / f"{run_id}.csv"
    json_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    records = report.get("records")
    if not isinstance(records, list) or not records:
        csv_path.write_text("", encoding="utf-8")
        return

    fieldnames = [
        "runId",
        "sequence",
        "sourceFile",
        "sourceTaskId",
        "sourceTaskItemId",
        "taskId",
        "taskItemId",
        "publishedAt",
        "firstSeenAt",
        "completedAt",
        "latencyMs",
        "status",
        "auditStatus",
        "attemptCount",
        "success",
        "summary",
        "lastError",
        "confidence",
        "weightedScore",
        "timedOut",
    ]
    with csv_path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        for record in records:
            writer.writerow({name: record.get(name) for name in fieldnames})


def _load_messages(path: Path) -> list[dict[str, Any]]:
    """兼容读取 JSON 数组和 JSONL 两种消息输入格式。"""
    raw = path.read_text(encoding="utf-8")
    if path.suffix == ".jsonl":
        messages = [json.loads(line) for line in raw.splitlines() if line.strip()]
    else:
        payload = json.loads(raw)
        if not isinstance(payload, list):
            raise ValueError(f"expected a JSON array in {path}")
        messages = payload
    return [message for message in messages if isinstance(message, dict)]


def _publish_messages(messages: list[dict[str, Any]], settings: AgentSettings, config: PressureTestConfig) -> None:
    """把消息批量发布到 RabbitMQ。"""
    try:
        import pika
    except ImportError as exc:  # pragma: no cover - optional runtime dependency
        raise RuntimeError("Missing pika dependency. Run pip install -r requirements.txt") from exc

    credentials = pika.PlainCredentials(settings.rabbitmq.username, settings.rabbitmq.password)
    parameters = pika.ConnectionParameters(
        host=settings.rabbitmq.host,
        port=settings.rabbitmq.port,
        virtual_host=settings.rabbitmq.vhost,
        credentials=credentials,
        heartbeat=settings.rabbitmq.heartbeat,
        blocked_connection_timeout=settings.rabbitmq.blocked_connection_timeout,
    )
    connection = pika.BlockingConnection(parameters)
    try:
        channel = connection.channel()
        # 隔离压测时允许顺手把 exchange/queue/binding 补出来，减少外部手工准备。
        if config.declare_topology:
            _declare_topology(channel, config, settings)
        properties = pika.BasicProperties(content_type="application/json", delivery_mode=2)
        for message in messages:
            body = json.dumps(message, ensure_ascii=False).encode("utf-8")
            channel.basic_publish(
                exchange=config.exchange,
                routing_key=config.routing_key,
                body=body,
                properties=properties,
            )
            if config.publish_interval_seconds > 0:
                time.sleep(config.publish_interval_seconds)
    finally:
        connection.close()


def _poll_until_finished(
    records: dict[str, dict[str, Any]],
    settings: AgentSettings,
    config: PressureTestConfig,
) -> dict[str, dict[str, Any]]:
    """持续轮询审核结果表，直到全部结束或超时。"""
    connection_factory = MySQLConnectionFactory(settings.mysql)
    deadline = time.monotonic() + config.timeout_seconds
    pending = set(records.keys())
    try:
        while pending and time.monotonic() < deadline:
            snapshots = _fetch_result_rows(connection_factory, pending)
            now = iso_utc_now()
            for task_item_id, snapshot in snapshots.items():
                record = records[task_item_id]
                apply_result_snapshot(record, snapshot, now)
                if record["status"] in TERMINAL_STATUSES:
                    pending.discard(task_item_id)
            if pending:
                time.sleep(config.poll_interval_seconds)

        if pending:
            timed_out_at = iso_utc_now()
            for task_item_id in pending:
                record = records[task_item_id]
                record["timedOut"] = True
                if not record["completedAt"]:
                    record["completedAt"] = timed_out_at
                if record["latencyMs"] is None:
                    record["latencyMs"] = elapsed_milliseconds(record["publishedAt"], record["completedAt"])
                if record["status"] == "published":
                    record["status"] = "timeout"
        return records
    finally:
        connection_factory.close()


def apply_result_snapshot(record: dict[str, Any], snapshot: dict[str, Any], observed_at: str) -> None:
    """把数据库中的审核结果快照合并进本地跟踪记录。"""
    if not record["firstSeenAt"]:
        record["firstSeenAt"] = observed_at
    status = str(snapshot.get("status") or record["status"])
    record["status"] = status
    record["auditStatus"] = str(snapshot.get("audit_status") or "")
    record["attemptCount"] = int(snapshot.get("attempt_count") or 0)
    record["summary"] = str(snapshot.get("summary") or "")
    record["lastError"] = str(snapshot.get("last_error") or "")
    record["confidence"] = float(snapshot.get("confidence") or 0)
    record["weightedScore"] = float(snapshot.get("weighted_score") or 0)
    if status in TERMINAL_STATUSES:
        record["completedAt"] = observed_at
        record["latencyMs"] = elapsed_milliseconds(record["publishedAt"], observed_at)
        record["success"] = status == "completed"


def elapsed_milliseconds(start_iso: str, end_iso: str) -> float:
    """计算两个 UTC 时间之间的毫秒差值。"""
    start = datetime.fromisoformat(start_iso.replace("Z", "+00:00"))
    end = datetime.fromisoformat(end_iso.replace("Z", "+00:00"))
    return round((end - start).total_seconds() * 1000, 2)


def _fetch_result_rows(
    connection_factory: MySQLConnectionFactory,
    task_item_ids: set[str],
) -> dict[str, dict[str, Any]]:
    """按 task_item_id 批量查询审核结果，减少轮询时的数据库往返次数。"""
    if not task_item_ids:
        return {}

    rows: dict[str, dict[str, Any]] = {}
    ordered_ids = sorted(task_item_ids)
    chunk_size = 100
    with connection_factory.connect() as connection:
        with connection.cursor() as cursor:
            # IN 查询按块执行，避免一次拼出过长 SQL。
            for start in range(0, len(ordered_ids), chunk_size):
                chunk = ordered_ids[start:start + chunk_size]
                placeholders = ", ".join(["%s"] * len(chunk))
                cursor.execute(
                    f"""
                    SELECT task_item_id, status, audit_status, attempt_count, summary,
                           last_error, confidence, weighted_score
                    FROM agent_audit_results
                    WHERE task_item_id IN ({placeholders})
                    """,
                    tuple(chunk),
                )
                for row in cursor.fetchall() or []:
                    rows[str(row["task_item_id"])] = row
    return rows


def iso_utc_now() -> str:
    """生成标准 UTC 时间字符串，统一报告时间格式。"""
    return datetime.now(timezone.utc).isoformat(timespec="milliseconds").replace("+00:00", "Z")


def _default_run_id() -> str:
    """生成默认运行 ID，便于区分不同批次压测。"""
    timestamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    return f"mq-pressure-{timestamp}-{uuid4().hex[:8]}"


def _declare_topology(channel: Any, config: PressureTestConfig, settings: AgentSettings) -> None:
    """声明隔离压测使用的 exchange、queue 和 binding。"""
    queue_name = config.queue_name or settings.rabbitmq.queue_name
    channel.exchange_declare(exchange=config.exchange, exchange_type="direct", durable=False, auto_delete=True)
    channel.queue_declare(queue=queue_name, durable=False, auto_delete=True)
    channel.queue_bind(queue=queue_name, exchange=config.exchange, routing_key=config.routing_key)
