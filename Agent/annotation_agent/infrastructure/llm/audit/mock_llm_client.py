"""审核链路使用的本地模拟 LLM 客户端。"""

from __future__ import annotations

import json
from typing import Any

from annotation_agent.infrastructure.llm.audit.heuristic_review_service import HeuristicReviewService


class MockLLMClient:
    """在没有真实 LLM 时，基于启发式规则生成审核结果。"""

    def __init__(self) -> None:
        self.heuristic_review_service = HeuristicReviewService()

    def invoke(self, _prompt: str, dimensions: list[dict[str, Any]], audit_context: dict[str, Any] | None, reason: str) -> str:
        """返回 JSON 字符串形式的模拟结果。"""
        result = self._mock_response(dimensions, reason, audit_context or {})
        return json.dumps(result, ensure_ascii=False)

    def invoke_structured(
        self,
        _prompt: Any,
        dimensions: list[dict[str, Any]],
        audit_context: dict[str, Any] | None,
        reason: str,
    ) -> dict[str, Any]:
        """返回字典形式的模拟结果。"""
        return self._mock_response(dimensions, reason, audit_context or {})

    def _mock_response(
        self,
        dimensions: list[dict[str, Any]],
        reason: str,
        audit_context: dict[str, Any],
    ) -> dict[str, Any]:
        """有上下文时走启发式审核，否则给默认评分。"""
        if audit_context:
            result = self.heuristic_review_service.review(
                audit_context.get("rawData", {}),
                audit_context.get("annotationResult", {}),
                dimensions,
                audit_context.get("validationIssues", []),
                audit_context.get("annotationSchema", []),
            )
            result["summary"] = f"{result['summary']} ({reason})"
            return result

        scores = []
        for dimension in dimensions:
            name = str(dimension.get("field") or dimension.get("name") or "").strip()
            if not name:
                continue
            scores.append(
                {
                    "dimension": name,
                    "score": 88,
                    "reason": f"{name}在模拟审核中表现可接受，但仍建议人工抽检。",
                }
            )
        return {
            "scores": scores,
            "summary": f"模拟审核完成：{reason}",
            "confidence": 0.82,
        }
