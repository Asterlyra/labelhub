"""LangChain retriever for curated suggestion examples."""

from __future__ import annotations

import json
import re
from typing import Any

from langchain_core.documents import Document
from langchain_core.retrievers import BaseRetriever
from pydantic import ConfigDict

from annotation_agent.infrastructure.retrieval.example_store import SuggestionExampleStore


_TOKEN_PATTERN = re.compile(r"[A-Za-z0-9\u4e00-\u9fff]+")


class SuggestionExampleRetriever(BaseRetriever):
    """Simple token-overlap retriever for suggestion examples."""

    model_config = ConfigDict(arbitrary_types_allowed=True)

    store: SuggestionExampleStore
    max_examples: int = 3

    def _get_relevant_documents(self, query: str, *, run_manager: Any = None) -> list[Document]:
        query_tokens = self._tokenize(query)
        ranked: list[tuple[float, dict[str, Any]]] = []
        for example in self.store.load_examples():
            search_text = self._build_search_text(example)
            example_tokens = self._tokenize(search_text)
            overlap = len(query_tokens & example_tokens)
            if overlap == 0:
                continue
            quality_score = float(example.get("qualityScore") or 0)
            ranked.append((overlap + quality_score * 0.01, example))

        ranked.sort(key=lambda item: item[0], reverse=True)
        return [
            Document(
                page_content=self._build_search_text(example),
                metadata={"example": example},
            )
            for _, example in ranked[: self.max_examples]
        ]

    async def _aget_relevant_documents(self, query: str, *, run_manager: Any = None) -> list[Document]:
        return self._get_relevant_documents(query, run_manager=run_manager)

    @staticmethod
    def _build_search_text(example: dict[str, Any]) -> str:
        return "\n".join(
            [
                str(example.get("taskTitle") or ""),
                str(example.get("taskDescription") or ""),
                " ".join(example.get("schemaFields") or []),
                json.dumps(example.get("rawData") or {}, ensure_ascii=False, sort_keys=True),
                " ".join(example.get("tags") or []),
            ]
        )

    @staticmethod
    def _tokenize(value: str) -> set[str]:
        return {token.lower() for token in _TOKEN_PATTERN.findall(value or "")}
