"""Agent统一启动入口。"""

from __future__ import annotations

import logging
import os

from annotation_agent import create_app
from annotation_agent.app.container import ServiceContainer

logger = logging.getLogger(__name__)


def _env_flag(name: str, default: str = "false") -> bool:
    """读取布尔型环境变量。"""
    return os.getenv(name, default).lower() == "true"


def _should_start_background_consumer(debug: bool) -> bool:
    """避免在 Flask reloader 父进程中重复启动 RabbitMQ 消费者。"""
    if not debug:
        return True
    return os.getenv("WERKZEUG_RUN_MAIN") == "true"


def main() -> int:
    container = ServiceContainer()
    app = create_app(container=container)

    debug = _env_flag("FLASK_DEBUG")
    host = os.getenv("FLASK_HOST", "0.0.0.0")
    port = int(os.getenv("FLASK_PORT", "5001"))

    consumer = None
    rabbitmq_enabled = container.settings.rabbitmq.enabled

    if rabbitmq_enabled and _should_start_background_consumer(debug):
        consumer = container.rabbitmq_audit_consumer.start_in_background()
        app.config["rabbitmq_audit_consumer"] = consumer
        logger.info("统一启动模式已启用：HTTP 服务和 RabbitMQ 审核消费者共用同一终端输出。")
    elif rabbitmq_enabled:
        logger.info("检测到 Flask reloader 父进程，暂不启动 RabbitMQ 审核消费者，等待子进程接管。")
    else:
        logger.info("统一启动模式运行中：仅启动 HTTP 服务。若需合并启动审核消费者，请设置 AGENT_ENABLE_RABBITMQ_CONSUMER=true。")

    logger.info("HTTP服务启动于 http://%s:%s", host, port)

    try:
        app.run(host=host, port=port, debug=debug)
    finally:
        if consumer is not None:
            logger.info("正在停止后台 RabbitMQ 审核消费者")
            consumer.stop()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
