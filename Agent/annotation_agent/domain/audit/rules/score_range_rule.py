"""评分字段校验规则。"""

from __future__ import annotations

from typing import Any

from annotation_agent.domain.audit.rules.validation_rule import issue, looks_like_score_field, option_value
from annotation_agent.domain.shared.task_schema import SchemaField


class ScoreRangeRule:
    """校验评分字段的值是否为数字且在有效范围内。"""

    def validate(self, field: SchemaField, value: Any, _annotation_result: dict[str, Any]) -> list[dict[str, Any]]:
        """校验评分字段：必须为数字，且在选项定义的范围内。"""
        if not looks_like_score_field(field):
            return []
        # 尝试将值转为数字
        try:
            numeric_value = float(value)
        except (TypeError, ValueError):
            return [
                issue(
                    field.field,
                    "high",
                    f"{field.label} must be numeric.",
                    "Submit a numeric score.",
                )
            ]

        # 从选项中提取数值范围，默认 1-5
        numeric_options = [
            option for option in (option_value(item) for item in field.options) if isinstance(option, (int, float))
        ]
        min_score, max_score = (min(numeric_options), max(numeric_options)) if numeric_options else (1, 5)
        if min_score <= numeric_value <= max_score:
            return []
        return [
            issue(
                field.field,
                "high",
                f"{field.label} is outside the valid score range.",
                f"Use a score between {min_score} and {max_score}.",
            )
        ]
