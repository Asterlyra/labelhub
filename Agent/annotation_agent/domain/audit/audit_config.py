"""审核配置领域模型。"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any


@dataclass
class AuditDimension:
    """单个审核维度，包含权重与通过分数线。"""
    field: str  # 维度字段名
    weight: float = 1.0  # 权重
    pass_score: int = 80  # 通过分数线

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "AuditDimension":
        """从字典构造审核维度实例。"""
        return cls(
            field=str(data.get("field", "")).strip(),
            weight=float(data.get("weight", 1.0)),
            pass_score=int(data.get("passScore", 80)),
        )

    def to_dict(self) -> dict[str, Any]:
        """转换为字典表示。"""
        return {
            "field": self.field,
            "weight": self.weight,
            "passScore": self.pass_score,
        }


@dataclass
class AuditConfig:
    """审核配置，包含提示词、维度列表和阈值。"""
    review_prompt: str = ""  # 审核提示词
    dimensions: list[AuditDimension] = field(default_factory=list)  # 审核维度列表
    pass_threshold: int = 85  # 通过阈值（加权分 >= 此值判定通过）
    review_threshold: int = 70  # 复审阈值（加权分 >= 此值判定人工复审）

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "AuditConfig":
        """从字典构造审核配置，至少需要一个维度。"""
        dimensions = [
            AuditDimension.from_dict(item)
            for item in data.get("dimensions", [])
            if item.get("field")
        ]
        if not dimensions:
            raise ValueError("audit config requires at least one dimension")

        return cls(
            review_prompt=str(data.get("reviewPrompt") or ""),
            dimensions=dimensions,
            pass_threshold=int(data.get("passThreshold", 85)),
            review_threshold=int(data.get("reviewThreshold", 70)),
        )

    def to_dict(self) -> dict[str, Any]:
        """转换为字典表示。"""
        return {
            "reviewPrompt": self.review_prompt,
            "dimensions": [dimension.to_dict() for dimension in self.dimensions],
            "passThreshold": self.pass_threshold,
            "reviewThreshold": self.review_threshold,
        }
