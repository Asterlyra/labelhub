"""多选字段校验规则。"""

from __future__ import annotations

from typing import Any

from annotation_agent.domain.audit.rules.validation_rule import issue, option_value
from annotation_agent.domain.shared.task_schema import SchemaField


class MultiChoiceRule:
    """校验多选字段（checkbox / multiSelect / tags）的值是否合法。"""
    MULTI_CHOICE_TYPES = {"checkbox", "multiSelect", "tags"}  # 适用的多选类型集合

    def validate(self, field: SchemaField, value: Any, _annotation_result: dict[str, Any]) -> list[dict[str, Any]]:
        """校验值是否为列表且所有项均在允许范围内。"""
        if field.type not in self.MULTI_CHOICE_TYPES:
            return []
        # 多选字段必须提交列表
        if not isinstance(value, list):
            return [
                issue(
                    field.field,
                    "high",
                    f"{field.label} must be submitted as a list.",
                    "Submit the multi-choice values as an array.",
                )
            ]
        if not field.options:
            return []

        # 检查每个选项是否在允许范围内
        allowed_values = {option_value(option) for option in field.options}
        illegal_values = []
        for item in value:
            try:
                is_allowed = item in allowed_values
            except TypeError:
                is_allowed = False
            if not is_allowed:
                illegal_values.append(item)

        if not illegal_values:
            return []
        return [
            issue(
                field.field,
                "high",
                f"{field.label} contains invalid options: {illegal_values}.",
                f"Choose only from {sorted(allowed_values, key=str)}.",
            )
        ]
