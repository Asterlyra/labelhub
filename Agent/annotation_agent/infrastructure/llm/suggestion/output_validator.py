"""建议输出校验器。"""

from __future__ import annotations

import json
from typing import Any

from annotation_agent.domain.shared.task_schema import SchemaField
from annotation_agent.infrastructure.llm.shared.output_validation import OutputValidationError
from annotation_agent.infrastructure.llm.suggestion.schema import MULTI_CHOICE_TYPES, SINGLE_CHOICE_TYPES, TEXT_TYPES


class SuggestionOutputValidator:
    """校验建议链路的 annotationData 输出。"""

    def parse_and_validate(self, raw_output: str, schema_fields: list[SchemaField]) -> dict[str, Any]:
        """解析原始 JSON 字符串并校验结构。"""
        return self.validate(self._parse_json_object(raw_output), schema_fields)

    def validate(self, model_result: dict[str, Any], schema_fields: list[SchemaField]) -> dict[str, Any]:
        """校验建议输出字段完整性、类型和值范围。"""
        if not isinstance(model_result, dict):
            raise OutputValidationError("suggestion output must be a JSON object")

        annotation_data = model_result.get("annotationData")
        if not isinstance(annotation_data, dict):
            raise OutputValidationError("annotationData must be an object")

        field_map = {field.field: field for field in schema_fields}

        unknown_fields = sorted(set(annotation_data.keys()) - set(field_map.keys()))
        if unknown_fields:
            raise OutputValidationError(f"annotationData contains unknown fields: {', '.join(unknown_fields)}")

        missing_fields = [field.field for field in schema_fields if field.field not in annotation_data]
        if missing_fields:
            raise OutputValidationError(f"annotationData missing fields: {', '.join(missing_fields)}")

        normalized_annotation_data: dict[str, Any] = {}
        for field in schema_fields:
            normalized_annotation_data[field.field] = self._validate_value(annotation_data.get(field.field), field)
        if not any(self._has_meaningful_value(value) for value in normalized_annotation_data.values()):
            raise OutputValidationError("annotationData must include at least one meaningful value")
        return {"annotationData": normalized_annotation_data}

    @staticmethod
    def _parse_json_object(raw_output: str) -> dict[str, Any]:
        """把原始字符串解析为 JSON 对象。"""
        if not isinstance(raw_output, str) or not raw_output.strip():
            raise OutputValidationError("suggestion output is empty")
        try:
            parsed_output = json.loads(raw_output.strip())
        except json.JSONDecodeError as exc:
            raise OutputValidationError("suggestion output is not valid JSON") from exc
        if not isinstance(parsed_output, dict):
            raise OutputValidationError("suggestion output must be a JSON object")
        return parsed_output

    def _validate_value(self, value: Any, schema_field: SchemaField) -> Any:
        """按字段类型分发到具体校验逻辑。"""
        field_type = schema_field.type
        if field_type in SINGLE_CHOICE_TYPES:
            normalized_value = self._validate_single_choice(value, schema_field)
        elif field_type in MULTI_CHOICE_TYPES:
            normalized_value = self._validate_multi_choice(value, schema_field)
        elif field_type in TEXT_TYPES:
            normalized_value = self._validate_text(value, schema_field)
        else:
            normalized_value = value
        self._validate_required(normalized_value, schema_field)
        return normalized_value

    @staticmethod
    def _validate_single_choice(value: Any, schema_field: SchemaField) -> Any:
        """校验单选值是否落在可选项中。"""
        if schema_field.options and value not in schema_field.options:
            raise OutputValidationError(f"annotationData.{schema_field.field} must be one of field options")
        return value

    @staticmethod
    def _validate_multi_choice(value: Any, schema_field: SchemaField) -> list[Any]:
        """校验多选值是否为合法数组。"""
        if not isinstance(value, list):
            raise OutputValidationError(f"annotationData.{schema_field.field} must be a list")
        invalid_values = [item for item in value if schema_field.options and item not in schema_field.options]
        if invalid_values:
            raise OutputValidationError(
                f"annotationData.{schema_field.field} contains option outside field options"
            )
        return value

    @staticmethod
    def _validate_text(value: Any, schema_field: SchemaField) -> str:
        """校验文本值，并处理长度约束。"""
        if not isinstance(value, str):
            raise OutputValidationError(f"annotationData.{schema_field.field} must be a string")
        normalized_value = value.strip()
        max_length = schema_field.config.get("maxLength")
        if isinstance(max_length, int) and len(normalized_value) > max_length:
            raise OutputValidationError(
                f"annotationData.{schema_field.field} exceeds maxLength {max_length}"
            )
        return normalized_value

    @staticmethod
    def _validate_required(value: Any, schema_field: SchemaField) -> None:
        """校验必填字段不为空。"""
        if not schema_field.required:
            return
        if isinstance(value, str) and not value.strip():
            raise OutputValidationError(f"annotationData.{schema_field.field} is required")
        if isinstance(value, list) and not value:
            raise OutputValidationError(f"annotationData.{schema_field.field} is required")
        if value is None:
            raise OutputValidationError(f"annotationData.{schema_field.field} is required")

    @staticmethod
    def _has_meaningful_value(value: Any) -> bool:
        """判断值是否包含对人工有意义的内容。"""
        if value is None:
            return False
        if isinstance(value, str):
            return bool(value.strip())
        if isinstance(value, list):
            return any(SuggestionOutputValidator._has_meaningful_value(item) for item in value)
        if isinstance(value, dict):
            return any(SuggestionOutputValidator._has_meaningful_value(item) for item in value.values())
        return True
