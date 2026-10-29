"""审核结果载荷工厂。"""

from __future__ import annotations

from typing import Any

from annotation_agent.domain.audit.audit_context import AuditContext
from annotation_agent.domain.audit.audit_result import AuditResult
from annotation_agent.domain.shared.enums import AuditStatus, TaskStatus


class AuditResultFactory:
    """根据审核上下文和模型输出构造 AuditResult 实例。"""

    def build_success_result(
        self,
        context: AuditContext,
        model_result: dict[str, Any],
        decision: dict[str, Any],
    ) -> AuditResult:
        """构建审核成功的结果实例。"""
        return AuditResult(
            task_id=context.request.task_id,
            task_item_id=context.request.task_item_id,
            status=TaskStatus.COMPLETED,
            audit_status=decision["decision"],
            confidence=decision["confidence"],
            summary=str(model_result.get("summary", "智能审核完成。")),
            scores=decision["scores"],
            weighted_score=decision["weightedScore"],
            media_result=context.prepared_media_evidence.to_media_result() if context.prepared_media_evidence else None,
            input_hash=context.input_hash,
        )

    def build_llm_failure_result(self, context: AuditContext) -> AuditResult:
        """构建 LLM 输出异常时的失败结果实例。"""
        # 为每个维度填充零分占位
        scores = [
            {
                "dimension": dimension.field,
                "score": 0,
                "reason": "模型输出无效，未能生成可用于决策的结构化评分。",
                "passScore": dimension.pass_score,
                "weight": dimension.weight,
            }
            for dimension in context.config.dimensions
        ]
        return AuditResult(
            task_id=context.request.task_id,
            task_item_id=context.request.task_item_id,
            status=TaskStatus.COMPLETED,
            audit_status=AuditStatus.REJECT,
            confidence=0.0,
            summary="智能自动预审失败：大模型输出未通过格式校验，已转入失败结果供人工排查。",
            scores=scores,
            weighted_score=0.0,
            media_result=context.prepared_media_evidence.to_media_result() if context.prepared_media_evidence else None,
            input_hash=context.input_hash,
        )
