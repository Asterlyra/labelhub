"""JSON编辑器字段校验规则。"""

from __future__ import annotations

import json
from typing import Any

from annotation_agent.domain.audit.rules.validation_rule import issue
from annotation_agent.domain.shared.task_schema import SchemaField


class JsonFieldRule:
    """校验 JSON 编辑器字段的值是否为合法 JSON。"""
    JSON_TYPES = {"json", "jsonEditor", "json-editor"}  # 适用的 JSON 类型集合

    def validate(self, field: SchemaField, value: Any, _annotation_result: dict[str, Any]) -> list[dict[str, Any]]:
        """校验值是否为合法的 JSON（dict/list 或可解析的字符串）。"""
        if field.type not in self.JSON_TYPES:
            return []
        # dict 或 list 类型直接通过
        if isinstance(value, (dict, list)):
            return []
        # 字符串类型尝试 JSON 解析
        if isinstance(value, str):
            try:
                json.loads(value)
                return []
            except json.JSONDecodeError:
                pass
        return [
            issue(
                field.field,
                "high",
                f"{field.label} is not valid JSON.",
                "Fix the JSON format and submit again.",
            )
        ]
