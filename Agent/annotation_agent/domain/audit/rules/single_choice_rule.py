"""单选字段校验规则。"""

from __future__ import annotations

from typing import Any

from annotation_agent.domain.audit.rules.validation_rule import issue, option_value
from annotation_agent.domain.shared.task_schema import SchemaField


class SingleChoiceRule:
    """校验单选字段（radio / select / rating）的值是否合法。"""
    CHOICE_TYPES = {"radio", "select", "rating"}  # 适用的单选类型集合

    def validate(self, field: SchemaField, value: Any, _annotation_result: dict[str, Any]) -> list[dict[str, Any]]:
        """校验值是否为允许的单选选项之一。"""
        if field.type not in self.CHOICE_TYPES or not field.options:
            return []

        allowed_values = {option_value(option) for option in field.options}
        # 单选字段不应提交列表
        if isinstance(value, list):
            return [
                issue(
                    field.field,
                    "high",
                    f"{field.label} expects a single value, not a list.",
                    f"Choose exactly one value from {sorted(allowed_values, key=str)}.",
                )
            ]

        try:
            if value in allowed_values:
                return []
        except TypeError:
            return [
                issue(
                    field.field,
                    "high",
                    f"{field.label} must be one allowed option.",
                    f"Choose exactly one value from {sorted(allowed_values, key=str)}.",
                )
            ]

        return [
            issue(
                field.field,
                "high",
                f"{field.label} is outside the allowed options.",
                f"Choose exactly one value from {sorted(allowed_values, key=str)}.",
            )
        ]
