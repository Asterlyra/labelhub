"""RabbitMQ审核Worker独立启动入口。"""

from __future__ import annotations

import logging
import sys

from annotation_agent.app.container import ServiceContainer


def _configure_logging() -> None:
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s [%(threadName)s] %(levelname)s %(name)s - %(message)s",
        datefmt="%Y-%m-%d %H:%M:%S",
        stream=sys.stdout,
    )
    logging.getLogger("pika").setLevel(logging.WARNING)


def main() -> int:
    _configure_logging()
    container = ServiceContainer()

    # 检查 mq 消费者是否启动
    if not container.settings.rabbitmq.enabled:
        logging.getLogger(__name__).error(
            "RabbitMQ消费者未启用，请先设置 AGENT_ENABLE_RABBITMQ_CONSUMER=true。"
        )
        return 1

    try:
        # 启动 RabbitMQ 消费者，阻塞式启动。
        container.rabbitmq_audit_consumer.start_blocking()
    except KeyboardInterrupt:
        logging.getLogger(__name__).info("收到中断信号，正在停止审核worker")
        container.rabbitmq_audit_consumer.stop()
    return 0


if __name__ == "__main__":
    # 0：程序正常退出
    # 1：程序异常退出
    raise SystemExit(main())
