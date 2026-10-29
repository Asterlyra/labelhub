"""校验规则协议与共享辅助函数。"""

from __future__ import annotations

from typing import Any, Protocol

from annotation_agent.domain.shared.task_schema import SchemaField


class ValidationRule(Protocol):
    """校验规则协议，所有规则需实现 validate 方法。"""

    def validate(self, field: SchemaField, value: Any, annotation_result: dict[str, Any]) -> list[dict[str, Any]]:
        """返回指定字段的违规问题列表。"""


def issue(field: str, level: str, message: str, suggestion: str) -> dict[str, Any]:
    """构造一条校验问题记录。"""
    return {
        "field": field,
        "level": level,
        "message": message,
        "suggestion": suggestion,
    }


def option_value(option: Any) -> Any:
    """提取选项的实际值，兼容 dict 和原始值两种格式。"""
    if isinstance(option, dict):
        return option.get("value", option.get("label"))
    return option


def is_empty(value: Any) -> bool:
    """判断值是否为空（None / 空字符串 / 空列表 / 空字典）。"""
    return value is None or value == "" or value == [] or value == {}


def looks_like_score_field(field: SchemaField) -> bool:
    """通过字段名、标签或类型判断是否为评分字段。"""
    text = f"{field.field} {field.label}".lower()
    return "score" in text or "评分" in text or field.type == "rating"
