"""审核决策策略。"""

from __future__ import annotations

import logging
from typing import Any

from annotation_agent.domain.audit.audit_config import AuditConfig
from annotation_agent.domain.shared.enums import AuditStatus

logger = logging.getLogger(__name__)


class AuditDecisionPolicy:
    """基于加权评分与校验问题决定审核结果（通过/复审/驳回）。"""

    def decide(
        self,
        model_result: dict[str, Any],
        config: AuditConfig,
        validation_issues: list[dict[str, Any]] | None = None,
    ) -> dict[str, Any]:
        """根据模型输出与配置做出审核决策。"""
        # 归一化各维度评分
        scores = self._normalize_scores(model_result.get("scores", []), config)
        # 计算加权总分
        weighted_score = self._calculate_weighted_score(scores, config)
        confidence = float(model_result.get("confidence", 0.0))
        validation_issues = validation_issues or []

        # 决策逻辑：高严重度问题直接驳回；否则按阈值判定
        if any(issue.get("level") == "high" for issue in validation_issues):
            decision = AuditStatus.REJECT
        elif weighted_score >= config.pass_threshold and self._all_dimensions_pass(scores, config):
            decision = AuditStatus.PASS
        elif weighted_score >= config.review_threshold:
            decision = AuditStatus.MANUAL_REVIEW
        else:
            decision = AuditStatus.REJECT

        # 存在非高严重度校验问题时，降级为人工复审
        if validation_issues and decision == AuditStatus.PASS:
            decision = AuditStatus.MANUAL_REVIEW

        logger.info(
            "审核决策已确定: weighted_score=%.2f confidence=%.2f decision=%s",
            weighted_score,
            confidence,
            decision,
        )
        return {
            "decision": decision,
            "confidence": max(0.0, min(confidence, 1.0)),
            "scores": scores,
            "weightedScore": weighted_score,
        }

    @staticmethod
    def _normalize_scores(scores: list[dict[str, Any]], config: AuditConfig) -> list[dict[str, Any]]:
        """将模型返回的评分归一化为配置中定义的维度顺序，并限制在 0-100 范围。"""
        score_map = {item.get("dimension"): item for item in scores}
        normalized_scores: list[dict[str, Any]] = []
        for dimension in config.dimensions:
            item = score_map.get(dimension.field, {})
            score = int(item.get("score", 0))
            normalized_scores.append(
                {
                    "dimension": dimension.field,
                    "score": max(0, min(score, 100)),
                    "reason": str(item.get("reason", "模型未返回该维度评分。")),
                    "passScore": dimension.pass_score,
                    "weight": dimension.weight,
                }
            )
        return normalized_scores

    @staticmethod
    def _calculate_weighted_score(scores: list[dict[str, Any]], config: AuditConfig) -> float:
        """按维度权重计算加权总分。"""
        weight_sum = sum(dimension.weight for dimension in config.dimensions) or 1.0
        return round(sum(item["score"] * item["weight"] for item in scores) / weight_sum, 2)

    @staticmethod
    def _all_dimensions_pass(scores: list[dict[str, Any]], config: AuditConfig) -> bool:
        """检查所有维度是否均达到各自的通过分数线。"""
        threshold_map = {dimension.field: dimension.pass_score for dimension in config.dimensions}
        return all(item["score"] >= threshold_map[item["dimension"]] for item in scores)
