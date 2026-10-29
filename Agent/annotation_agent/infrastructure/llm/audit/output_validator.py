"""审核输出校验器。"""

from __future__ import annotations

import json
from typing import Any

from annotation_agent.infrastructure.llm.shared.output_validation import OutputValidationError


class OutputValidator:
    """校验审核链路的结构化输出。"""

    def parse_and_validate(self, raw_output: str, dimensions: list[dict[str, Any]]) -> dict[str, Any]:
        """解析原始 JSON 字符串并校验审核结果。"""
        return self.validate(self._parse_json_object(raw_output), dimensions)

    def validate(self, model_result: dict[str, Any], dimensions: list[dict[str, Any]]) -> dict[str, Any]:
        """校验已解析的审核输出字典。"""
        if not isinstance(model_result, dict):
            raise OutputValidationError("LLM output must be a JSON object")

        self._require_keys(model_result, {"scores", "summary", "confidence"})
        normalized_scores = self._validate_scores(model_result.get("scores"), dimensions)
        confidence = self._validate_confidence(model_result.get("confidence"))
        summary = model_result.get("summary")
        if not isinstance(summary, str) or not summary.strip():
            raise OutputValidationError("summary must be a non-empty string")

        return {
            "scores": normalized_scores,
            "summary": summary.strip(),
            "confidence": confidence,
        }

    @staticmethod
    def _parse_json_object(raw_output: str) -> dict[str, Any]:
        """把原始字符串解析为 JSON 对象。"""
        if not isinstance(raw_output, str) or not raw_output.strip():
            raise OutputValidationError("LLM output is empty")
        try:
            parsed_output = json.loads(raw_output.strip())
        except json.JSONDecodeError as exc:
            raise OutputValidationError("LLM output is not valid JSON") from exc
        if not isinstance(parsed_output, dict):
            raise OutputValidationError("LLM output must be a JSON object")
        return parsed_output

    @staticmethod
    def _require_keys(model_result: dict[str, Any], required_keys: set[str]) -> None:
        """检查顶层必填字段。"""
        missing_keys = sorted(required_keys - set(model_result.keys()))
        if missing_keys:
            raise OutputValidationError(f"missing required fields: {', '.join(missing_keys)}")

    def _validate_scores(self, scores: Any, dimensions: list[dict[str, Any]]) -> list[dict[str, Any]]:
        """校验各维度评分列表，并按配置顺序标准化。"""
        if not isinstance(scores, list):
            raise OutputValidationError("scores must be a list")

        expected_dimensions = [str(item.get("field", "")).strip() for item in dimensions if item.get("field")]
        score_map: dict[str, dict[str, Any]] = {}
        for index, score_item in enumerate(scores):
            if not isinstance(score_item, dict):
                raise OutputValidationError(f"scores[{index}] must be an object")
            dimension = str(score_item.get("dimension", "")).strip()
            if not dimension:
                raise OutputValidationError(f"scores[{index}].dimension must be a non-empty string")
            if dimension in score_map:
                raise OutputValidationError(f"duplicate score dimension: {dimension}")
            score_map[dimension] = score_item

        normalized_scores = []
        for dimension in expected_dimensions:
            score_item = score_map.get(dimension)
            if score_item is None:
                raise OutputValidationError(f"missing score for dimension: {dimension}")
            normalized_scores.append(
                {
                    "dimension": dimension,
                    "score": self._validate_score_value(score_item.get("score"), dimension),
                    "reason": self._validate_reason(score_item.get("reason"), dimension),
                }
            )
        return normalized_scores

    @staticmethod
    def _validate_score_value(value: Any, dimension: str) -> int:
        """校验单个评分取值。"""
        if isinstance(value, bool):
            raise OutputValidationError(f"{dimension} score must be numeric between 0 and 100")
        try:
            score = float(value)
        except (TypeError, ValueError) as exc:
            raise OutputValidationError(f"{dimension} score must be numeric between 0 and 100") from exc
        if score < 0 or score > 100:
            raise OutputValidationError(f"{dimension} score must be between 0 and 100")
        return int(round(score))

    @staticmethod
    def _validate_reason(value: Any, dimension: str) -> str:
        """校验评分理由。"""
        if not isinstance(value, str) or not value.strip():
            raise OutputValidationError(f"{dimension} reason must be a non-empty string")
        return value.strip()

    @staticmethod
    def _validate_confidence(value: Any) -> float:
        """校验置信度范围。"""
        if isinstance(value, bool):
            raise OutputValidationError("confidence must be between 0 and 1")
        try:
            confidence = float(value)
        except (TypeError, ValueError) as exc:
            raise OutputValidationError("confidence must be between 0 and 1") from exc
        if confidence < 0 or confidence > 1:
            raise OutputValidationError("confidence must be between 0 and 1")
        return confidence
