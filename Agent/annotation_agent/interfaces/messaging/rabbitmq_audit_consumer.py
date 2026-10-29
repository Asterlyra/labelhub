"""后端审核事件的RabbitMQ消费者。"""

from __future__ import annotations

import json
import logging
import threading
import time
from concurrent.futures import Executor, ThreadPoolExecutor
from typing import Any, Callable

from annotation_agent.app.settings import RabbitMQSettings
from annotation_agent.application.audit.audit_job_service import AuditJobService, MaxRetriesExceededError

logger = logging.getLogger(__name__)


class RabbitMQConsumerUnavailable(RuntimeError):
    """当RabbitMQ依赖不可用时抛出的异常。"""


class ErrorLogThrottler:
    """对同类错误日志做限频，避免故障时刷屏。"""

    def __init__(self, interval_seconds: float) -> None:
        self._interval_seconds = max(1.0, interval_seconds)
        self._state: dict[str, dict[str, float | int]] = {}
        self._lock = threading.Lock()

    def exception(self, key: str, message: str, *args: Any) -> None:
        should_log, suppressed_count = self._register(key)
        if not should_log:
            return
        suffix = self._build_suffix(suppressed_count)
        logger.exception(message + suffix, *args)

    def error(self, key: str, message: str, *args: Any) -> None:
        should_log, suppressed_count = self._register(key)
        if not should_log:
            return
        suffix = self._build_suffix(suppressed_count)
        logger.error(message + suffix, *args)

    def _register(self, key: str) -> tuple[bool, int]:
        now = time.monotonic()
        with self._lock:
            state = self._state.get(key)
            if state is None or now - float(state["last_logged_at"]) >= self._interval_seconds:
                suppressed_count = int(state["suppressed_count"]) if state else 0
                self._state[key] = {"last_logged_at": now, "suppressed_count": 0}
                return True, suppressed_count

            state["suppressed_count"] = int(state["suppressed_count"]) + 1
            return False, 0

    def _build_suffix(self, suppressed_count: int) -> str:
        if suppressed_count <= 0:
            return ""
        return "；在日志限频窗口内已省略 %s 条同类错误" % suppressed_count


class RabbitMQAuditConsumer:
    """RabbitMQ审核消费者，监听后端推送的审核事件并驱动审核流程。"""

    def __init__(
        self,
        rabbitmq_settings: RabbitMQSettings,
        audit_job_service: AuditJobService,
        executor: Executor | None = None,
    ) -> None:
        self.rabbitmq_settings = rabbitmq_settings
        self.audit_job_service = audit_job_service
        self._connection: Any | None = None
        self._channel: Any | None = None
        self._executor = executor
        self._owns_executor = executor is None
        self._stop_requested = threading.Event()
        self._error_log_throttler = ErrorLogThrottler(
            self.rabbitmq_settings.error_log_interval_seconds
        )

    def start_blocking(self) -> None:
        """建立连接并阻塞式消费消息。"""
        self._stop_requested.clear()
        self._ensure_executor()

        retry_delay = self.rabbitmq_settings.reconnect_base_delay_seconds
        max_retry_delay = max(
            self.rabbitmq_settings.reconnect_base_delay_seconds,
            self.rabbitmq_settings.reconnect_max_delay_seconds,
        )

        while not self._stop_requested.is_set():
            try:
                self._connect()
                logger.info(
                    "RabbitMQ审核消费者已启动: queue=%s, concurrency=%s, prefetch=%s",
                    self.rabbitmq_settings.queue_name,
                    self.rabbitmq_settings.worker_concurrency,
                    self._effective_prefetch_count(),
                )
                retry_delay = self.rabbitmq_settings.reconnect_base_delay_seconds
                self._channel.start_consuming()
            except RabbitMQConsumerUnavailable:
                self._cleanup()
                raise
            except Exception:
                self._cleanup()
                if self._stop_requested.is_set():
                    break
                self._error_log_throttler.exception(
                    "rabbitmq-connection-loop",
                    "RabbitMQ连接或消费异常，将在 %.1f 秒后重试",
                    retry_delay,
                )
                self._wait_before_retry(retry_delay)
                retry_delay = min(max_retry_delay, max(retry_delay * 2, retry_delay + 1))
            else:
                self._cleanup()

        self._cleanup()
        self._shutdown_executor()

    def start_in_background(self) -> "RabbitMQAuditConsumer | None":
        """在守护线程中启动消费者，未启用时返回None。"""
        if not self.rabbitmq_settings.enabled:
            logger.info("RabbitMQ消费者已禁用，跳过启动")
            return None
        thread = threading.Thread(
            target=self.start_blocking,
            name="agent-rabbitmq-audit-consumer",
            daemon=True,
        )
        thread.start()
        return self

    def stop(self) -> None:
        """停止消费并关闭连接。"""
        self._stop_requested.set()
        connection = self._connection
        if connection and connection.is_open:
            connection.add_callback_threadsafe(self._stop_consuming_callback)

    def _cleanup(self) -> None:
        channel = self._channel
        connection = self._connection
        self._channel = None
        self._connection = None
        if channel and getattr(channel, "is_open", False):
            try:
                channel.close()
            except Exception:
                logger.debug("关闭RabbitMQ channel失败", exc_info=True)
        if connection and getattr(connection, "is_open", False):
            try:
                connection.close()
            except Exception:
                logger.debug("关闭RabbitMQ connection失败", exc_info=True)

    def _shutdown_executor(self) -> None:
        if self._executor is None or not self._owns_executor:
            return
        self._executor.shutdown(wait=True)
        self._executor = None

    def _ensure_executor(self) -> None:
        if self._executor is None:
            self._executor = ThreadPoolExecutor(
                max_workers=self.rabbitmq_settings.worker_concurrency,
                thread_name_prefix="audit-worker",
            )

    def _stop_consuming_callback(self) -> None:
        if self._channel and self._channel.is_open:
            self._channel.stop_consuming()

    def _connect(self) -> None:
        """建立RabbitMQ连接并配置消费参数。"""
        try:
            import pika
        except ImportError as exc:
            raise RabbitMQConsumerUnavailable(
                "Missing pika dependency. Install requirements.txt before enabling RabbitMQ."
            ) from exc

        credentials = pika.PlainCredentials(
            self.rabbitmq_settings.username,
            self.rabbitmq_settings.password,
        )
        parameters = pika.ConnectionParameters(
            host=self.rabbitmq_settings.host,
            port=self.rabbitmq_settings.port,
            virtual_host=self.rabbitmq_settings.vhost,
            credentials=credentials,
            heartbeat=self.rabbitmq_settings.heartbeat,
            blocked_connection_timeout=self.rabbitmq_settings.blocked_connection_timeout,
        )
        self._connection = pika.BlockingConnection(parameters)
        self._channel = self._connection.channel()
        self._declare_topology()
        self._channel.basic_qos(prefetch_count=self._effective_prefetch_count())
        self._channel.basic_consume(
            queue=self.rabbitmq_settings.queue_name,
            on_message_callback=self._on_message,
            auto_ack=False,
        )

    def _declare_topology(self) -> None:
        """Ensure the shared audit exchange/queue/binding exists before consuming."""
        if self._channel is None:
            return
        self._channel.exchange_declare(
            exchange=self.rabbitmq_settings.exchange_name,
            exchange_type="direct",
            durable=True,
            auto_delete=False,
        )
        self._channel.queue_declare(
            queue=self.rabbitmq_settings.queue_name,
            durable=True,
            auto_delete=False,
        )
        self._channel.queue_bind(
            queue=self.rabbitmq_settings.queue_name,
            exchange=self.rabbitmq_settings.exchange_name,
            routing_key=self.rabbitmq_settings.routing_key,
        )

    def _effective_prefetch_count(self) -> int:
        configured = max(1, self.rabbitmq_settings.prefetch_count)
        return max(configured, self.rabbitmq_settings.worker_concurrency)

    def _on_message(self, channel: Any, method: Any, _properties: Any, body: bytes) -> None:
        """处理单条RabbitMQ消息的回调。"""
        if self._executor is None:
            logger.error("RabbitMQ消费者线程池未初始化，消息将重新入队")
            channel.basic_nack(
                delivery_tag=method.delivery_tag,
                requeue=self.rabbitmq_settings.requeue_on_failure,
            )
            return
        try:
            self._executor.submit(self._process_message, method.delivery_tag, body)
        except RuntimeError:
            self._error_log_throttler.exception(
                "rabbitmq-executor-closed",
                "RabbitMQ消费者线程池已关闭，消息将重新入队",
            )
            channel.basic_nack(
                delivery_tag=method.delivery_tag,
                requeue=self.rabbitmq_settings.requeue_on_failure,
            )

    def _process_message(self, delivery_tag: Any, body: bytes) -> None:
        task_item_id = ""
        try:
            event = self._decode_message(body)
            task_item_id = str(event.get("taskItemId") or "unknown")
            self.audit_job_service.process_backend_event(event)
            self._schedule_ack(delivery_tag)
        except MaxRetriesExceededError:
            logger.error("重试次数已耗尽, task_item_id=%s，确认消息", task_item_id)
            self._schedule_ack(delivery_tag)
        except Exception:
            self._error_log_throttler.exception(
                "rabbitmq-message-processing-failure",
                "RabbitMQ审核消息处理失败: task_item_id=%s",
                task_item_id,
            )
            self._schedule_nack(delivery_tag, self.rabbitmq_settings.requeue_on_failure)

    def _wait_before_retry(self, delay_seconds: float) -> None:
        if delay_seconds <= 0:
            return
        self._stop_requested.wait(delay_seconds)

    def _schedule_ack(self, delivery_tag: Any) -> None:
        self._schedule_channel_callback(lambda: self._ack(delivery_tag))

    def _schedule_nack(self, delivery_tag: Any, requeue: bool) -> None:
        self._schedule_channel_callback(lambda: self._nack(delivery_tag, requeue))

    def _schedule_channel_callback(self, callback: Callable[[], None]) -> None:
        connection = self._connection
        if connection and connection.is_open and not self._stop_requested.is_set():
            connection.add_callback_threadsafe(callback)

    def _ack(self, delivery_tag: Any) -> None:
        if self._channel and self._channel.is_open:
            self._channel.basic_ack(delivery_tag=delivery_tag)

    def _nack(self, delivery_tag: Any, requeue: bool) -> None:
        if self._channel and self._channel.is_open:
            self._channel.basic_nack(delivery_tag=delivery_tag, requeue=requeue)

    @staticmethod
    def _decode_message(body: bytes) -> dict[str, Any]:
        """将消息体解码为JSON字典。"""
        try:
            payload = json.loads(body.decode("utf-8"))
        except json.JSONDecodeError as exc:
            raise ValueError("RabbitMQ audit message body is not valid JSON") from exc
        if not isinstance(payload, dict):
            raise ValueError("RabbitMQ audit message body must be a JSON object")
        return payload
