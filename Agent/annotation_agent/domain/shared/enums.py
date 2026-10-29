"""共享领域枚举。"""

from enum import Enum


# 审核状态枚举
class AuditStatus(str, Enum):
    PASS = "pass"  # 通过
    MANUAL_REVIEW = "manual_review"  # 人工复审
    REJECT = "reject"  # 驳回


# 任务状态枚举
class TaskStatus(str, Enum):
    PENDING = "pending"  # 待处理
    PROCESSING = "processing"  # 处理中
    COMPLETED = "completed"  # 已完成
    FAILED = "failed"  # 失败
    MAX_RETRIES_EXCEEDED = "max_retries_exceeded"  # 超过最大重试次数
