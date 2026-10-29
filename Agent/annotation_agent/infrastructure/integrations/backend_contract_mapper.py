"""后端事件到内部审核载荷的映射器。"""

from __future__ import annotations

from typing import Any

from annotation_agent.infrastructure.integrations.material_schema_adapter import materials_to_annotation_schema


def backend_context_to_audit_payload(context: dict[str, Any]) -> dict[str, Any]:
    """将后端事件上下文转换为内部审核载荷格式。"""
    review_config = _ensure_dict(context.get("reviewConfig"))
    annotation_schema = _ensure_dict(context.get("annotationSchema"))
    task_id = _stringify(context.get("taskId"))
    task_title = _stringify(context.get("taskTitle"))
    task_description = _stringify(context.get("taskDescription"))

    return {
        "taskId": task_id,
        "taskItemId": _stringify(context.get("taskItemId")),
        "taskTitle": task_title,
        "taskDescription": task_description,
        "rawData": context.get("rawData") or {},
        # 若无显式标注模式，则从素材列表自动生成
        "annotationSchema": annotation_schema or materials_to_annotation_schema(_ensure_list(context.get("materials"))),
        # 兼容annotationResult和annotationData两种字段名
        "annotationResult": (
            context.get("annotationResult")
            if context.get("annotationResult") is not None
            else context.get("annotationData") or {}
        ),
        "reviewConfig": _normalize_review_config(review_config),
    }


def _normalize_review_config(review_config: dict[str, Any]) -> dict[str, Any]:
    """规范化审核配置，为缺失字段填充默认值。"""
    return {
        "reviewPrompt": _stringify(review_config.get("reviewPrompt")),
        "dimensions": [
            {
                "name": _stringify(item.get("field")),
                "field": _stringify(item.get("field")),
                "weight": _to_float(item.get("weight"), 1.0),
                "passScore": int(_to_float(item.get("passScore"), 80)),
            }
            for item in _ensure_list(review_config.get("dimensions"))
            if isinstance(item, dict) and _stringify(item.get("field"))
        ],
        "passThreshold": int(_to_float(review_config.get("passThreshold"), 85)),
        "reviewThreshold": int(_to_float(review_config.get("reviewThreshold"), 70)),
    }


def _ensure_dict(value: Any) -> dict[str, Any]:
    """确保值为字典类型，否则返回空字典。"""
    return value if isinstance(value, dict) else {}


def _ensure_list(value: Any) -> list[Any]:
    """确保值为列表类型，否则返回空列表。"""
    return value if isinstance(value, list) else []


def _stringify(value: Any) -> str:
    """将值转换为字符串，None转为空字符串。"""
    return "" if value is None else str(value)


def _to_float(value: Any, default: float) -> float:
    """将值转换为浮点数，失败时返回默认值。"""
    try:
        return float(value)
    except (TypeError, ValueError):
        return default
