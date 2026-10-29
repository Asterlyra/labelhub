"""Tests for first-stage suggestion retrieval and tool helpers."""

from __future__ import annotations

import json

from annotation_agent.infrastructure.retrieval.example_store import SuggestionExampleStore
from annotation_agent.infrastructure.retrieval.suggestion_example_retriever import SuggestionExampleRetriever
from annotation_agent.infrastructure.tools.suggestion_tools import SuggestionTools


def test_example_store_loads_valid_jsonl_examples(tmp_path):
    examples_path = tmp_path / "examples.jsonl"
    examples_path.write_text(
        "\n".join(
            [
                json.dumps(
                    {
                        "taskTitle": "Summary quality task",
                        "taskDescription": "Check summary coverage",
                        "schemaFields": ["相关性评分"],
                        "rawData": {"prompt": "Summarize the article"},
                        "annotationData": {"相关性评分": "4"},
                        "tags": ["summary"],
                        "qualityScore": 90,
                    },
                    ensure_ascii=False,
                ),
                "{\"broken\": ",
            ]
        ),
        encoding="utf-8",
    )

    loaded = SuggestionExampleStore(examples_path).load_examples()

    assert len(loaded) == 1
    assert loaded[0]["taskTitle"] == "Summary quality task"
    assert loaded[0]["annotationData"] == {"相关性评分": "4"}


def test_example_retriever_returns_most_relevant_examples(tmp_path):
    examples_path = tmp_path / "examples.jsonl"
    examples_path.write_text(
        "\n".join(
            [
                json.dumps(
                    {
                        "taskTitle": "Summary quality task",
                        "taskDescription": "Check summary coverage",
                        "schemaFields": ["相关性评分"],
                        "rawData": {"prompt": "Summarize the article"},
                        "annotationData": {"相关性评分": "4"},
                        "tags": ["summary"],
                        "qualityScore": 90,
                    },
                    ensure_ascii=False,
                ),
                json.dumps(
                    {
                        "taskTitle": "Billing answer review",
                        "taskDescription": "Check factual billing answers",
                        "schemaFields": ["准确性评分"],
                        "rawData": {"prompt": "Explain billing rules"},
                        "annotationData": {"准确性评分": "3"},
                        "tags": ["billing"],
                        "qualityScore": 70,
                    },
                    ensure_ascii=False,
                ),
            ]
        ),
        encoding="utf-8",
    )
    retriever = SuggestionExampleRetriever(store=SuggestionExampleStore(examples_path), max_examples=2)

    docs = retriever.invoke("summary article coverage")

    assert len(docs) == 1
    assert docs[0].metadata["example"]["taskTitle"] == "Summary quality task"


def test_suggestion_tools_summarize_schema(tmp_path):
    examples_path = tmp_path / "examples.jsonl"
    examples_path.write_text("", encoding="utf-8")
    tools = SuggestionTools(
        retriever=SuggestionExampleRetriever(store=SuggestionExampleStore(examples_path), max_examples=2),
    )

    schema_summary = tools.inspect_annotation_schema.invoke(
        {
            "schema_fields": [
                {"field": "quality", "required": True, "options": ["1", "2"]},
                {"field": "comment", "required": False},
            ]
        }
    )

    assert schema_summary["requiredFields"] == ["quality"]
    assert schema_summary["optionalFields"] == ["comment"]
    assert schema_summary["constrainedFields"]["quality"] == ["1", "2"]
