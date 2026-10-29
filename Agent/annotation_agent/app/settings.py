"""Agent服务的运行时配置。"""

from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path

try:
    from dotenv import load_dotenv
except ImportError:  # pragma: no cover - optional dependency
    def load_dotenv(*_args, **_kwargs):
        return False


# 项目根目录，用于定位.env文件
BASE_DIR = Path(__file__).resolve().parents[2]
load_dotenv(BASE_DIR / ".env")


@dataclass(frozen=True)
class LLMSettings:
    """大语言模型相关配置。"""

    provider: str
    base_url: str | None
    api_key: str | None
    model_name: str
    temperature: float
    use_mock: bool


@dataclass(frozen=True)
class MySQLSettings:
    """MySQL数据库连接配置。"""

    host: str
    port: int
    user: str
    password: str
    database: str
    charset: str
    connect_timeout: int
    pool_size: int
    pool_timeout: int
    pool_recycle_seconds: int


@dataclass(frozen=True)
class RabbitMQSettings:
    """RabbitMQ消息队列连接与消费配置。"""

    enabled: bool
    host: str
    port: int
    username: str
    password: str
    vhost: str
    exchange_name: str
    routing_key: str
    queue_name: str
    prefetch_count: int
    worker_concurrency: int
    heartbeat: int
    blocked_connection_timeout: int
    requeue_on_failure: bool
    max_retries: int
    reconnect_base_delay_seconds: float = 1.0
    reconnect_max_delay_seconds: float = 30.0
    error_log_interval_seconds: float = 30.0


@dataclass(frozen=True)
class MediaProcessingSettings:
    """多媒体处理配置。"""

    download_timeout_seconds: float
    video_timeout_seconds: float
    max_visual_evidence: int


@dataclass(frozen=True)
class SuggestionPipelineSettings:
    """Suggestion pipeline related settings."""

    examples_path: str
    max_examples: int


@dataclass(frozen=True)
class AgentSettings:
    """Agent服务的全局配置聚合。"""

    llm: LLMSettings
    mysql: MySQLSettings
    rabbitmq: RabbitMQSettings
    media: MediaProcessingSettings
    suggestion: SuggestionPipelineSettings


def load_settings() -> AgentSettings:
    """从环境变量加载并返回AgentSettings实例。"""
    return AgentSettings(
        llm=LLMSettings(
            provider=os.getenv("AI_AUDIT_PROVIDER", "openai_compatible").lower(),
            base_url=os.getenv("AI_AUDIT_BASE_URL") or None,
            api_key=os.getenv("AI_AUDIT_API_KEY") or None,
            model_name=os.getenv("AI_AUDIT_MODEL", "gpt-4o-mini"),
            temperature=float(os.getenv("AI_AUDIT_TEMPERATURE", "0")),
            use_mock=os.getenv("AI_AUDIT_USE_MOCK", "false").lower() == "true",
        ),
        mysql=MySQLSettings(
            host=os.getenv("AGENT_MYSQL_HOST", "127.0.0.1"),
            port=int(os.getenv("AGENT_MYSQL_PORT", "3306")),
            user=os.getenv("AGENT_MYSQL_USER", "root"),
            password=os.getenv("AGENT_MYSQL_PASSWORD", ""),
            database=os.getenv("AGENT_MYSQL_DATABASE", "labelhub_agent"),
            charset=os.getenv("AGENT_MYSQL_CHARSET", "utf8mb4"),
            connect_timeout=int(os.getenv("AGENT_MYSQL_CONNECT_TIMEOUT", "5")),
            pool_size=int(os.getenv("AGENT_MYSQL_POOL_SIZE", "8")),
            pool_timeout=int(os.getenv("AGENT_MYSQL_POOL_TIMEOUT", "5")),
            pool_recycle_seconds=int(os.getenv("AGENT_MYSQL_POOL_RECYCLE_SECONDS", "1800")),
        ),
        rabbitmq=RabbitMQSettings(
            enabled=os.getenv("AGENT_ENABLE_RABBITMQ_CONSUMER", "false").lower() == "true",
            host=os.getenv("RABBITMQ_HOST", "127.0.0.1"),
            port=int(os.getenv("RABBITMQ_PORT", "5672")),
            username=os.getenv("RABBITMQ_USERNAME", "guest"),
            password=os.getenv("RABBITMQ_PASSWORD", "guest"),
            vhost=os.getenv("RABBITMQ_VHOST", "/"),
            exchange_name=os.getenv("RABBITMQ_AI_REVIEW_EXCHANGE", "ai.review.exchange"),
            routing_key=os.getenv("RABBITMQ_AI_REVIEW_ROUTING_KEY", "ai.review"),
            queue_name=os.getenv("RABBITMQ_AI_REVIEW_QUEUE", "ai.review.queue"),
            prefetch_count=int(os.getenv("RABBITMQ_PREFETCH_COUNT", "1")),
            worker_concurrency=int(os.getenv("RABBITMQ_WORKER_CONCURRENCY", "4")),
            heartbeat=int(os.getenv("RABBITMQ_HEARTBEAT", "60")),
            blocked_connection_timeout=int(os.getenv("RABBITMQ_BLOCKED_CONNECTION_TIMEOUT", "30")),
            requeue_on_failure=os.getenv("RABBITMQ_REQUEUE_ON_FAILURE", "true").lower() == "true",
            max_retries=int(os.getenv("RABBITMQ_MAX_RETRIES", "3")),
            reconnect_base_delay_seconds=float(os.getenv("RABBITMQ_RECONNECT_BASE_DELAY_SECONDS", "1")),
            reconnect_max_delay_seconds=float(os.getenv("RABBITMQ_RECONNECT_MAX_DELAY_SECONDS", "30")),
            error_log_interval_seconds=float(os.getenv("RABBITMQ_ERROR_LOG_INTERVAL_SECONDS", "30")),
        ),
        media=MediaProcessingSettings(
            download_timeout_seconds=float(os.getenv("AI_AUDIT_MEDIA_DOWNLOAD_TIMEOUT", "10")),
            video_timeout_seconds=float(os.getenv("AI_AUDIT_VIDEO_TIMEOUT", "30")),
            max_visual_evidence=int(os.getenv("AI_AUDIT_MAX_VISUAL_EVIDENCE", "6")),
        ),
        suggestion=SuggestionPipelineSettings(
            examples_path=os.getenv(
                "AI_SUGGESTION_EXAMPLES_PATH",
                str(BASE_DIR / "annotation_agent" / "infrastructure" / "retrieval" / "examples" / "suggestion_examples.jsonl"),
            ),
            max_examples=int(os.getenv("AI_SUGGESTION_MAX_EXAMPLES", "3")),
        ),
    )
