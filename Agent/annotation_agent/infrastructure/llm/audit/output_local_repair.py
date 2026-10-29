"""审核输出的本地修复逻辑。"""

from __future__ import annotations

import json
from dataclasses import dataclass
from typing import Any


@dataclass(frozen=True)
class AuditLocalRepairResult:
    repaired_result: dict[str, Any]
    actions: list[str]


class AuditOutputLocalRepair:
    """在触发 LLM 重试前，先做保守的本地修复。"""

    def try_repair(
        self,
        model_result: dict[str, Any],
        dimensions: list[dict[str, Any]],
    ) -> AuditLocalRepairResult | None:
        if not isinstance(model_result, dict):
            return None

        repaired_result = dict(model_result)
        actions: list[str] = []

        summary = repaired_result.get("summary")
        if isinstance(summary, str):
            trimmed_summary = summary.strip()
            if trimmed_summary != summary:
                repaired_result["summary"] = trimmed_summary
                actions.append("summary: trimmed surrounding whitespace")

        scores = repaired_result.get("scores")
        if isinstance(scores, list):
            repaired_scores, score_actions = self._repair_scores(scores, dimensions)
            if score_actions:
                repaired_result["scores"] = repaired_scores
                actions.extend(score_actions)

        if not actions:
            return None

        return AuditLocalRepairResult(repaired_result=repaired_result, actions=actions)

    @staticmethod
    def _repair_scores(
        scores: list[Any],
        dimensions: list[dict[str, Any]],
    ) -> tuple[list[Any], list[str]]:
        """清理噪声项和重复评分项。"""
        expected_dimensions = {
            str(item.get("field", "")).strip()
            for item in dimensions
            if str(item.get("field", "")).strip()
        }
        repaired_scores: list[Any] = []
        seen_markers: set[str] = set()
        removed_noise = 0
        removed_duplicates = 0

        for score_item in scores:
            if not isinstance(score_item, dict):
                removed_noise += 1
                continue

            dimension = str(score_item.get("dimension", "")).strip()
            if not dimension:
                removed_noise += 1
                continue

            if expected_dimensions and dimension not in expected_dimensions:
                repaired_scores.append(score_item)
                continue

            normalized_reason = score_item.get("reason")
            if isinstance(normalized_reason, str):
                normalized_reason = normalized_reason.strip()
            marker = json.dumps(
                {
                    "dimension": dimension,
                    "score": score_item.get("score"),
                    "reason": normalized_reason,
                },
                ensure_ascii=False,
                sort_keys=True,
            )
            if marker in seen_markers:
                removed_duplicates += 1
                continue
            seen_markers.add(marker)

            normalized_score_item = dict(score_item)
            normalized_score_item["dimension"] = dimension
            if isinstance(score_item.get("reason"), str):
                normalized_score_item["reason"] = normalized_reason
            repaired_scores.append(normalized_score_item)

        actions: list[str] = []
        if removed_noise:
            actions.append(f"scores: removed {removed_noise} malformed score entries")
        if removed_duplicates:
            actions.append(f"scores: removed {removed_duplicates} duplicate score entries")
        return repaired_scores, actions
