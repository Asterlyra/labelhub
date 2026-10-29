"""审核链路的结构化输出模型。"""

from __future__ import annotations

from pydantic import BaseModel, ConfigDict, Field


class StrictSchemaModel(BaseModel):
    """严格 schema 基类，禁止额外字段。"""

    model_config = ConfigDict(extra="forbid")


class AuditScoreItem(StrictSchemaModel):
    """单个审核维度的评分项。"""

    dimension: str = Field(description="Dimension name from reviewConfig.dimensions[*].field.")
    score: int = Field(description="Dimension score between 0 and 100.")
    reason: str = Field(description="Reason for the score.")


class AuditStructuredOutput(StrictSchemaModel):
    """审核结构化输出。"""

    scores: list[AuditScoreItem] = Field(description="Per-dimension review scores.")
    summary: str = Field(description="Overall audit summary.")
    confidence: float = Field(description="Model confidence between 0 and 1.")


AUDIT_RESULT_SCHEMA = AuditStructuredOutput
