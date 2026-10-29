"""建议输出的本地修复逻辑。"""

from __future__ import annotations

import json
from dataclasses import dataclass
from typing import Any

from annotation_agent.domain.shared.task_schema import SchemaField
from annotation_agent.infrastructure.llm.suggestion.schema import MULTI_CHOICE_TYPES, TEXT_TYPES


@dataclass(frozen=True)
class SuggestionLocalRepairResult:
    repaired_result: dict[str, Any]
    actions: list[str]


class SuggestionOutputLocalRepair:
    """在建议链路里先做低风险本地修复，再决定是否重试模型。"""

    def try_repair(
        self,
        model_result: dict[str, Any],
        schema_fields: list[SchemaField],
    ) -> SuggestionLocalRepairResult | None:
        if not isinstance(model_result, dict):
            return None

        annotation_data = model_result.get("annotationData")
        if not isinstance(annotation_data, dict):
            return None

        repaired_annotation_data = dict(annotation_data)
        actions: list[str] = []

        for schema_field in schema_fields:
            if schema_field.field not in repaired_annotation_data:
                continue

            value = repaired_annotation_data[schema_field.field]
            repaired_value = value
            field_actions: list[str] = []

            if schema_field.type in TEXT_TYPES:
                repaired_value, field_actions = self._repair_text(value, schema_field)
            elif schema_field.type in MULTI_CHOICE_TYPES:
                repaired_value, field_actions = self._repair_multi_choice(value, schema_field)

            if field_actions:
                repaired_annotation_data[schema_field.field] = repaired_value
                actions.extend(field_actions)

        if not actions:
            return None

        repaired_result = dict(model_result)
        repaired_result["annotationData"] = repaired_annotation_data
        return SuggestionLocalRepairResult(repaired_result=repaired_result, actions=actions)

    @staticmethod
    def _repair_text(value: Any, schema_field: SchemaField) -> tuple[Any, list[str]]:
        """修剪文本值，并在可选字段上兜底空值。"""
        actions: list[str] = []
        if value is None:
            if schema_field.required:
                return value, actions
            return "", [f"{schema_field.field}: normalized optional null text to empty string"]

        if not isinstance(value, str):
            return value, actions

        repaired_value = value.strip()
        if repaired_value != value:
            actions.append(f"{schema_field.field}: trimmed surrounding whitespace")

        max_length = schema_field.config.get("maxLength")
        if isinstance(max_length, int) and len(repaired_value) > max_length:
            repaired_value = repaired_value[:max_length]
            actions.append(f"{schema_field.field}: truncated text to maxLength {max_length}")

        return repaired_value, actions

    @staticmethod
    def _repair_multi_choice(value: Any, schema_field: SchemaField) -> tuple[Any, list[str]]:
        """去掉多选值中的重复项，并在可选字段上兜底空值。"""
        if value is None:
            if schema_field.required:
                return value, []
            return [], [f"{schema_field.field}: normalized optional null multi-choice to empty list"]

        if not isinstance(value, list):
            return value, []

        repaired_list: list[Any] = []
        seen: set[str] = set()
        removed_duplicates = 0
        for item in value:
            marker = json.dumps(item, ensure_ascii=False, sort_keys=True)
            if marker in seen:
                removed_duplicates += 1
                continue
            seen.add(marker)
            repaired_list.append(item)

        if removed_duplicates == 0:
            return value, []

        return repaired_list, [f"{schema_field.field}: removed {removed_duplicates} duplicate multi-choice values"]
