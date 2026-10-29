"""建议链路的结构化输出模型与动态 schema 生成器。"""

from __future__ import annotations

from typing import Union

try:
    from typing import TypeAlias
except ImportError:  # pragma: no cover - Python 3.9 fallback
    from typing_extensions import TypeAlias

from pydantic import BaseModel, ConfigDict, Field

from annotation_agent.domain.shared.task_schema import SchemaField

PrimitiveValue: TypeAlias = Union[str, int, float, bool]
SuggestionValue: TypeAlias = Union[PrimitiveValue, list[PrimitiveValue]]

TEXT_TYPES = {"text", "input", "textarea", "richText"}
SINGLE_CHOICE_TYPES = {"radio", "select", "rating"}
MULTI_CHOICE_TYPES = {"checkbox", "tagSelect", "multiSelect"}


class StrictSchemaModel(BaseModel):
    """严格 schema 基类，禁止额外字段。"""

    model_config = ConfigDict(extra="forbid")


class SuggestionStructuredOutput(StrictSchemaModel):
    """建议链路的默认结构化输出。"""

    annotationData: dict[str, SuggestionValue] = Field(description="Suggested annotation values.")


def build_suggestion_structured_schema(schema_fields: list[SchemaField]) -> dict:
    """根据标注 schema 动态生成 suggestion 的 JSON Schema。"""
    properties = {}
    required_fields: list[str] = []
    for field in schema_fields:
        properties[field.field] = _build_field_schema(field)
        required_fields.append(field.field)

    return {
        "type": "object",
        "title": "SuggestionAnnotationOutput",
        "additionalProperties": False,
        "required": ["annotationData"],
        "properties": {
            "annotationData": {
                "type": "object",
                "title": "AnnotationData",
                "additionalProperties": False,
                "properties": properties,
                "required": required_fields,
            }
        },
    }


def _build_field_schema(field: SchemaField) -> dict[str, object]:
    """为单个 schema 字段构建对应的 JSON Schema 子结构。"""
    if field.type in SINGLE_CHOICE_TYPES and field.options:
        return {
            "description": f"{field.field}的建议值。",
            "enum": list(field.options),
        }
    if field.type in MULTI_CHOICE_TYPES:
        item_schema: dict[str, object] = {"type": "string"}
        if field.options:
            item_schema = {"enum": list(field.options)}
        return {
            "type": "array",
            "description": f"{field.field}的建议值（数组）。",
            "items": item_schema,
        }
    if field.type in TEXT_TYPES:
        return {
            "type": "string",
            "description": f"{field.field}的建议文本。",
        }
    return {
        "type": "string",
        "description": f"{field.field}的建议值。",
    }


SUGGESTION_RESULT_SCHEMA = SuggestionStructuredOutput
