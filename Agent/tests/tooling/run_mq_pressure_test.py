"""发布生成好的 MQ 消息，并输出逐条处理结果报告。"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parents[2]
DEFAULT_INPUT_PATH = BASE_DIR / "tests" / "fixtures" / "generated" / "all_mq_messages.jsonl"
DEFAULT_REPORT_DIR = BASE_DIR / "tests" / "fixtures" / "generated" / "reports"

if str(BASE_DIR) not in sys.path:
    sys.path.insert(0, str(BASE_DIR))

from annotation_agent.tooling.mq_pressure_test_runner import (
    DEFAULT_EXCHANGE,
    DEFAULT_ROUTING_KEY,
    PressureTestConfig,
    declare_topology,
    run_pressure_test,
)


def build_parser() -> argparse.ArgumentParser:
    """定义压测脚本命令行参数。"""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--input",
        type=Path,
        default=DEFAULT_INPUT_PATH,
        help=f"generated MQ messages file (default: {DEFAULT_INPUT_PATH})",
    )
    parser.add_argument(
        "--report-dir",
        type=Path,
        default=DEFAULT_REPORT_DIR,
        help=f"report directory (default: {DEFAULT_REPORT_DIR})",
    )
    parser.add_argument("--exchange", default=DEFAULT_EXCHANGE, help="RabbitMQ exchange name")
    parser.add_argument("--routing-key", default=DEFAULT_ROUTING_KEY, help="RabbitMQ routing key")
    parser.add_argument("--queue-name", default=None, help="RabbitMQ queue name used by the worker")
    parser.add_argument(
        "--declare-topology",
        action="store_true",
        help="declare a direct exchange + queue + binding before publishing",
    )
    parser.add_argument(
        "--declare-topology-only",
        action="store_true",
        help="declare exchange + queue + binding and exit without publishing",
    )
    parser.add_argument("--poll-interval", type=float, default=1.0, help="MySQL poll interval in seconds")
    parser.add_argument("--timeout", type=float, default=180.0, help="max wait time in seconds")
    parser.add_argument("--publish-interval", type=float, default=0.0, help="sleep between publishes in seconds")
    parser.add_argument("--limit", type=int, default=None, help="only publish the first N messages")
    parser.add_argument("--run-id", default="", help="optional fixed run id")
    return parser


def main() -> int:
    """执行压测脚本主流程。"""
    args = build_parser().parse_args()
    config = PressureTestConfig(
        input_path=args.input,
        report_dir=args.report_dir,
        exchange=args.exchange,
        routing_key=args.routing_key,
        queue_name=args.queue_name,
        declare_topology=args.declare_topology,
        poll_interval_seconds=args.poll_interval,
        timeout_seconds=args.timeout,
        publish_interval_seconds=args.publish_interval,
        limit=args.limit,
        run_id=args.run_id,
    )
    if args.declare_topology_only:
        # 只声明 RabbitMQ 拓扑，不真正发消息，适合先启动隔离 worker。
        topology = declare_topology(config)
        print(
            "declared topology: "
            f"exchange={topology['exchange']} "
            f"routing_key={topology['routingKey']} "
            f"queue={topology['queueName']}"
        )
        return 0

    report = run_pressure_test(config)
    summary = report["summary"]
    print(
        f"pressure test run: {report['runId']}\n"
        f"messages={report['messageCount']} "
        f"success={summary['successCount']} "
        f"failed={summary['failedCount']} "
        f"max_retries={summary['maxRetriesExceededCount']} "
        f"timed_out={summary['timedOutCount']}"
    )
    print(f"report dir: {args.report_dir}")
    return 0 if summary["timedOutCount"] == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
