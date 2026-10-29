"""审核请求契约。"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any

from annotation_agent.domain.shared.task_schema import normalize_annotation_schema


@dataclass
class AuditRequest:
    """审核请求的数据载体，封装从后端推送过来的审核负载。"""

    task_id: str
    task_item_id: str = ""
    task_title: str = ""
    task_description: str = ""
    raw_data: dict[str, Any] | str = field(default_factory=dict)
    annotation_result: dict[str, Any] | str = field(default_factory=dict)
    annotation_schema: dict[str, Any] | list[Any] = field(default_factory=dict)
    review_config: dict[str, Any] | None = None

    @classmethod
    def from_payload(cls, task_id: str, payload: dict[str, Any] | None) -> "AuditRequest":
        """从原始负载构造审核请求实例。"""
        payload = payload or {}
        cls._validate_payload(payload)
        return cls(
            task_id=task_id,
            task_item_id=str(payload.get("taskItemId") or ""),
            task_title=str(payload.get("taskTitle") or ""),
            task_description=str(payload.get("taskDescription") or ""),
            raw_data=payload["rawData"],
            annotation_result=payload["annotationResult"],
            annotation_schema=normalize_annotation_schema(payload["annotationSchema"]),
            review_config=payload.get("reviewConfig") if isinstance(payload.get("reviewConfig"), dict) else None,
        )

    @staticmethod
    def _validate_payload(payload: dict[str, Any]) -> None:
        """校验审核负载中的必填字段及类型。"""
        required_fields = ("rawData", "annotationSchema", "annotationResult", "reviewConfig")
        missing_fields = [field_name for field_name in required_fields if field_name not in payload]
        if missing_fields:
            raise ValueError(f"missing required audit payload field: {', '.join(missing_fields)}")
        if not isinstance(payload["annotationSchema"], dict):
            raise ValueError("annotationSchema must be an object with a components array")
        if not isinstance(payload["annotationResult"], dict):
            raise ValueError("annotationResult must be an object")
        if not isinstance(payload["reviewConfig"], dict):
            raise ValueError("reviewConfig must be an object")
