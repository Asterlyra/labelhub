"""LangChain tools used by the suggestion pipeline."""

from __future__ import annotations

from typing import Any

from langchain_core.tools import StructuredTool

from annotation_agent.infrastructure.retrieval.suggestion_example_retriever import SuggestionExampleRetriever


class SuggestionTools:
    """Bundles tool instances used by suggestion skills."""

    def __init__(self, retriever: SuggestionExampleRetriever) -> None:
        self.retriever = retriever
        self.retrieve_similar_examples = StructuredTool.from_function(
            func=self._retrieve_similar_examples,
            name="retrieve_similar_examples",
            description="Retrieve a few curated annotation examples similar to the current suggestion task.",
        )
        self.inspect_annotation_schema = StructuredTool.from_function(
            func=self._inspect_annotation_schema,
            name="inspect_annotation_schema",
            description="Summarize required fields, optional fields, and field option constraints.",
        )

    def _retrieve_similar_examples(
        self,
        task_title: str,
        task_description: str,
        schema_fields: list[dict[str, Any]],
        raw_data: dict[str, Any],
    ) -> list[dict[str, Any]]:
        schema_names = [
            str(field.get("field") or "")
            for field in schema_fields
            if isinstance(field, dict) and str(field.get("field") or "").strip()
        ]
        query = "\n".join(
            [
                task_title or "",
                task_description or "",
                " ".join(schema_names),
                self._truncate_value(raw_data),
            ]
        )
        documents = self.retriever.invoke(query)
        return [
            dict(document.metadata.get("example") or {})
            for document in documents
            if isinstance(document.metadata.get("example"), dict)
        ]

    @staticmethod
    def _inspect_annotation_schema(schema_fields: list[dict[str, Any]]) -> dict[str, Any]:
        required_fields: list[str] = []
        optional_fields: list[str] = []
        constrained_fields: dict[str, list[str]] = {}

        for item in schema_fields:
            if not isinstance(item, dict):
                continue
            field_name = str(item.get("field") or "").strip()
            if not field_name:
                continue
            if bool(item.get("required")):
                required_fields.append(field_name)
            else:
                optional_fields.append(field_name)

            options = item.get("options")
            if isinstance(options, list) and options:
                constrained_fields[field_name] = [str(option) for option in options]

        return {
            "requiredFields": required_fields,
            "optionalFields": optional_fields,
            "constrainedFields": constrained_fields,
        }

    @staticmethod
    def _truncate_scalar(value: Any) -> Any:
        if isinstance(value, str):
            text = value.strip()
            return text[:280] + ("..." if len(text) > 280 else "")
        if isinstance(value, list):
            return [SuggestionTools._truncate_scalar(item) for item in value[:3]]
        if isinstance(value, dict):
            return {str(key): SuggestionTools._truncate_scalar(item) for key, item in list(value.items())[:3]}
        return value

    @staticmethod
    def _truncate_value(value: Any) -> str:
        normalized = SuggestionTools._truncate_scalar(value)
        return str(normalized)
