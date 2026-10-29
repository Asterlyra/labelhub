"""后端素材到标注模式的适配器。"""

from __future__ import annotations

from typing import Any


# 后端组件标签到内部字段类型的映射表（支持中英文标签）
FIELD_TYPE_BY_LABEL = {
    "单选框": "radio",
    "radio": "radio",
    "多选框": "checkbox",
    "checkbox": "checkbox",
    "下拉选项框": "select",
    "select": "select",
    "输入框": "text",
    "text": "text",
    "文本说明": "display",
    "display": "display",
    "详情页内容": "textarea",
    "多行文本框": "textarea",
    "textarea": "textarea",
    "图片组件": "text",
    "文件组件": "text",
    "评分组件": "rating",
    "rating": "rating",
}


def materials_to_annotation_schema(materials: list[Any] | None) -> dict[str, Any]:
    """将后端素材列表转换为标注模式（包含components数组）。"""
    return {
        "components": [material_to_component(item) for item in _ensure_list(materials)],
    }


def material_to_component(material: Any) -> dict[str, Any]:
    """将单个后端素材转换为标注组件定义。"""
    item = _ensure_dict(material)
    config = _ensure_dict(item.get("config"))
    options = config.get("options")
    multiple = bool(config.get("multiple"))
    component_label = _stringify(item.get("label") or item.get("field"))
    component_type = FIELD_TYPE_BY_LABEL.get(component_label, "text")

    # 下拉框且支持多选时，转换为multiSelect类型
    if component_type == "select" and multiple:
        component_type = "multiSelect"

    component = {
        "field": _stringify(item.get("field")),
        "label": _stringify(item.get("field") or component_label),
        "type": component_type,
        "required": _read_required(item.get("required")),
        "config": config,
    }
    if isinstance(options, list):
        component["options"] = options
    return component


def _read_required(value: Any) -> bool:
    """将各种格式的必填标志统一转换为布尔值。"""
    if isinstance(value, bool):
        return value
    if isinstance(value, (int, float)):
        return value != 0
    return str(value).lower() in {"1", "true", "yes"}


def _ensure_dict(value: Any) -> dict[str, Any]:
    """确保值为字典类型，否则返回空字典。"""
    return value if isinstance(value, dict) else {}


def _ensure_list(value: Any) -> list[Any]:
    """确保值为列表类型，否则返回空列表。"""
    return value if isinstance(value, list) else []


def _stringify(value: Any) -> str:
    """将值转换为字符串，None转为空字符串。"""
    return "" if value is None else str(value)
