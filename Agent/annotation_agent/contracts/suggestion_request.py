"""建议请求契约。"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any

from annotation_agent.domain.shared.task_schema import normalize_annotation_schema
from annotation_agent.infrastructure.integrations.material_schema_adapter import materials_to_annotation_schema


@dataclass
class SuggestionRequest:
    """建议请求的数据载体，封装前端提交的标注建议负载。"""

    task_id: str
    task_item_id: str
    project_id: str = ""
    task_title: str = ""
    task_description: str = ""
    materials: list[dict[str, Any]] = field(default_factory=list)
    raw_data: dict[str, Any] = field(default_factory=dict)
    annotation_schema: dict[str, Any] = field(default_factory=dict)

    @classmethod
    def from_payload(cls,
                     payload: dict[str, Any] | None) -> "SuggestionRequest":
        """从原始负载构造建议请求实例。"""
        payload = payload or {}
        cls._validate_payload(payload)

        body_task_id = str(payload.get("taskId") or "").strip()

        materials = [item
                     for item in payload["materials"]
                     if isinstance(item, dict)]

        annotation_schema = normalize_annotation_schema(
            materials_to_annotation_schema(materials)
        )

        return cls(
            task_id=body_task_id,
            task_item_id=str(payload["taskItemId"]),
            task_title=str(payload.get("taskTitle") or ""),
            task_description=str(payload.get("taskDescription") or ""),
            materials=materials,
            raw_data=dict(payload["rawData"]),
            annotation_schema=annotation_schema,
        )

    def to_payload(self) -> dict[str, Any]:
        """将建议请求转换为字典格式，便于序列化传输。"""
        return {
            "taskId": self.task_id,
            "taskItemId": self.task_item_id,
            "taskTitle": self.task_title,
            "taskDescription": self.task_description,
            "materials": self.materials,
            "rawData": self.raw_data,
        }

    @staticmethod
    def _validate_payload(payload: dict[str, Any]) -> None:
        """校验建议负载中的必填字段及类型。"""
        required_fields = ("taskId", "taskItemId", "materials", "rawData")
        missing_fields = [field_name for field_name in required_fields if field_name not in payload]
        if missing_fields:
            raise ValueError(f"missing required suggestion payload field: {', '.join(missing_fields)}")
        if not isinstance(payload["materials"], list):
            raise ValueError("materials must be an array")
        if not isinstance(payload["rawData"], dict):
            raise ValueError("rawData must be an object")
