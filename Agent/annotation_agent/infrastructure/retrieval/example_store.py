"""Local example store for suggestion few-shot retrieval."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any


class SuggestionExampleStore:
    """Loads curated suggestion examples from a local JSONL file."""

    def __init__(self, file_path: str | Path) -> None:
        self.file_path = Path(file_path)

    def load_examples(self) -> list[dict[str, Any]]:
        if not self.file_path.exists():
            return []

        examples: list[dict[str, Any]] = []
        for line in self.file_path.read_text(encoding="utf-8").splitlines():
            raw_line = line.strip()
            if not raw_line:
                continue
            try:
                parsed = json.loads(raw_line)
            except json.JSONDecodeError:
                continue
            if isinstance(parsed, dict):
                normalized = self._normalize_example(parsed)
                if normalized:
                    examples.append(normalized)
        return examples

    @staticmethod
    def _normalize_example(example: dict[str, Any]) -> dict[str, Any] | None:
        annotation_data = example.get("annotationData")
        if not isinstance(annotation_data, dict) or not annotation_data:
            return None

        schema_fields = example.get("schemaFields")
        if not isinstance(schema_fields, list):
            schema_fields = []

        raw_data = example.get("rawData")
        if not isinstance(raw_data, dict):
            raw_data = {}

        tags = example.get("tags")
        if not isinstance(tags, list):
            tags = []

        return {
            "taskTitle": str(example.get("taskTitle") or ""),
            "taskDescription": str(example.get("taskDescription") or ""),
            "schemaFields": [str(item) for item in schema_fields if str(item).strip()],
            "rawData": raw_data,
            "annotationData": annotation_data,
            "tags": [str(item) for item in tags if str(item).strip()],
            "qualityScore": float(example.get("qualityScore") or 0),
        }

