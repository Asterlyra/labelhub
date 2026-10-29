"""审核结果领域模型。"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone
from typing import Any

from annotation_agent.domain.shared.enums import AuditStatus, TaskStatus


# 中国标准时间（UTC+8）
CN_TIMEZONE = timezone(timedelta(hours=8))


def current_timestamp() -> str:
    """返回当前北京时间的 ISO 格式时间戳。"""
    return datetime.now(CN_TIMEZONE).isoformat()


@dataclass
class AuditResult:
    """审核结果，记录任务状态、评分与决策详情。"""
    task_id: str  # 任务 ID
    task_item_id: str  # 任务子项 ID
    status: TaskStatus  # 任务状态
    audit_status: AuditStatus  # 审核状态（通过/复审/驳回）
    confidence: float  # 模型置信度
    summary: str  # 审核摘要
    scores: list[dict[str, Any]]  # 各维度评分列表
    weighted_score: float = 0.0  # 加权总分
    media_result: dict[str, Any] | None = None  # 多媒体处理结果
    input_hash: str = ""  # 输入内容哈希
    attempt_count: int = 0  # 已尝试次数
    last_error: str = ""  # 最近一次错误信息
    created_at: str = field(default_factory=current_timestamp)  # 创建时间

    def to_dict(self) -> dict[str, Any]:
        """转换为可序列化的字典。"""
        return {
            "taskId": self.task_id,
            "taskItemId": self.task_item_id,
            "status": self.status,
            "auditStatus": self.audit_status,
            "confidence": self.confidence,
            "weightedScore": self.weighted_score,
            "summary": self.summary,
            "scores": self.scores,
            "mediaResult": self.media_result,
            "inputHash": self.input_hash,
            "attemptCount": self.attempt_count,
            "lastError": self.last_error,
            "createdAt": self.created_at,
        }
