"""必填字段校验规则。"""

from __future__ import annotations

from typing import Any

from annotation_agent.domain.audit.rules.validation_rule import is_empty, issue
from annotation_agent.domain.shared.task_schema import SchemaField


class RequiredFieldRule:
    """检查必填字段是否缺失。"""

    def validate(self, field: SchemaField, value: Any, _annotation_result: dict[str, Any]) -> list[dict[str, Any]]:
        """非必填或已有值时通过；否则返回高严重度问题。"""
        if not field.required or not is_empty(value):
            return []
        return [
            issue(
                field.field,
                "high",
                f"{field.label} is required but missing from annotationResult.",
                "Please fill the field before submitting again.",
            )
        ]
