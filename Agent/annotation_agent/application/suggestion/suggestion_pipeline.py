"""LangChain-powered first-stage suggestion pipeline."""

from __future__ import annotations

import json
import logging
from typing import Any

from langchain_core.runnables import RunnableBranch, RunnableLambda, RunnableParallel

logger = logging.getLogger(__name__)

from annotation_agent.application.suggestion.skills.common import SuggestionPipelineResult, SuggestionStageResult
from annotation_agent.application.suggestion.skills.draft_generation_skill import DraftGenerationSkill
from annotation_agent.application.suggestion.skills.fewshot_retrieval_skill import FewShotRetrievalSkill
from annotation_agent.application.suggestion.skills.schema_repair_skill import SchemaRepairSkill
from annotation_agent.contracts.suggestion_request import SuggestionRequest
from annotation_agent.domain.shared.task_context import TaskContext
from annotation_agent.infrastructure.llm.shared.prompt_messages import PromptMessages
from annotation_agent.infrastructure.tools.suggestion_tools import SuggestionTools


class SuggestionPipeline:
    """Orchestrates retrieval, drafting, and repair for suggestions."""

    def __init__(
        self,
        tools: SuggestionTools,
        fewshot_skill: FewShotRetrievalSkill,
        draft_skill: DraftGenerationSkill,
        schema_repair_skill: SchemaRepairSkill,
    ) -> None:
        self.tools = tools
        self.fewshot_skill = fewshot_skill
        self.draft_skill = draft_skill
        self.schema_repair_skill = schema_repair_skill

    def run(
        self,
        request: SuggestionRequest,
        context: TaskContext,
        base_prompt: PromptMessages,
        mock_raw_output: str,
    ) -> SuggestionPipelineResult:
        logger.info("[建议流水线] 开始执行，任务ID: %s", getattr(context, "task_id", "未知"))
        logger.debug("[建议流水线] 请求原始数据: %s", getattr(request, "raw_data", None))

        context_bundle = {
            "request": request,
            "context": context,
            "base_prompt": base_prompt,
            "mock_raw_output": mock_raw_output,
        }

        gather_context = RunnableParallel(
            schema_summary=RunnableLambda(
                lambda payload: self.tools.inspect_annotation_schema.invoke(
                    {"schema_fields": [field.to_dict() for field in payload["context"].schema_fields]}
                )
            ),
            similar_examples=RunnableBranch(
                (
                    RunnableLambda(
                        lambda payload: bool(payload["request"].raw_data) or bool(payload["context"].schema_fields)
                    ),
                    RunnableLambda(lambda payload: self.fewshot_skill.run(payload["request"], payload["context"])),
                ),
                RunnableLambda(lambda _payload: []),
            ),
            passthrough=RunnableLambda(lambda payload: payload),
        )

        logger.info("[建议流水线] 正在收集上下文（schema摘要 + 相似示例）...")
        enriched = gather_context.invoke(context_bundle)
        logger.info("[建议流水线] 上下文收集完成，相似示例数: %d", len(enriched.get("similar_examples") or []))
        return self._run_generation_stages(enriched)

    def _run_generation_stages(self, enriched: dict[str, Any]) -> SuggestionPipelineResult:
        passthrough = enriched["passthrough"]
        request: SuggestionRequest = passthrough["request"]
        context: TaskContext = passthrough["context"]
        base_prompt: PromptMessages = passthrough["base_prompt"]
        mock_raw_output: str = passthrough["mock_raw_output"]
        schema_summary = enriched["schema_summary"]
        similar_examples = enriched["similar_examples"]

        stage_records: list[SuggestionStageResult] = []

        logger.info("[生成阶段] 开始草稿生成...")
        draft_result = self.draft_skill.run(
            request=request,
            context=context,
            base_prompt=base_prompt,
            similar_examples=similar_examples,
            schema_summary=schema_summary,
            mock_raw_output=mock_raw_output,
        )
        stage_records.append(draft_result)
        logger.info(
            "[生成阶段] 草稿生成完成，标注数据: %s，错误数: %d",
            "有" if draft_result.annotation_data is not None else "无",
            len(draft_result.llm_trace.get("errors") or []),
        )
        if draft_result.llm_trace.get("errors"):
            logger.debug("[生成阶段] 草稿生成错误详情: %s", draft_result.llm_trace.get("errors"))

        if draft_result.annotation_data is None:
            logger.warning("[生成阶段] 草稿未通过校验，进入Schema修复阶段...")
            repaired = self.schema_repair_skill.run(
                context=context,
                base_prompt=base_prompt,
                invalid_raw_output=draft_result.raw_output,
                errors=list(draft_result.llm_trace.get("errors") or []),
                schema_summary=schema_summary,
                mock_raw_output=mock_raw_output,
            )
            stage_records.append(repaired)
            logger.info(
                "[生成阶段] Schema修复完成，标注数据: %s",
                "有" if repaired.annotation_data is not None else "无",
            )
        else:
            logger.info("[生成阶段] 草稿校验通过，跳过Schema修复")

        return self._finalize(stage_records)

    @staticmethod
    def _finalize(stage_records: list[SuggestionStageResult]) -> SuggestionPipelineResult:
        logger.info("[汇总] 开始汇总流水线结果，共 %d 个阶段记录", len(stage_records))

        successful_stage = next((stage
                                 for stage in reversed(stage_records)
                                 if stage.annotation_data is not None), None)

        final_stage = successful_stage or stage_records[-1]
        logger.info(
            "[汇总] 最终结果来源阶段: %s，是否有标注数据: %s",
            final_stage.stage_name,
            final_stage.annotation_data is not None,
        )

        aggregated_prompt = "\n\n-----\n\n".join(stage.prompt_text
                                                 for stage
                                                 in stage_records if stage.prompt_text)
        aggregated_raw_output = json.dumps(
            [
                {
                    "stage": stage.stage_name,
                    "rawOutput": stage.raw_output,
                    "llmTrace": stage.llm_trace,
                }
                for stage in stage_records
            ],
            ensure_ascii=False,
        )
        stage_trace = {
            "attempts": sum(int(stage.llm_trace.get("attempts") or 0) for stage in stage_records),
            "errors": [
                error
                for stage in stage_records
                for error in list(stage.llm_trace.get("errors") or [])
            ],
            "success": successful_stage is not None,
            "mode": "pipeline",
            "stages": [stage.stage_name for stage in stage_records],
        }

        total_errors = len(stage_trace["errors"])
        logger.info(
            "[汇总] 流水线执行完成 — 成功: %s，总尝试次数: %d，总错误数: %d，阶段: %s",
            stage_trace["success"],
            stage_trace["attempts"],
            total_errors,
            stage_trace["stages"],
        )
        if total_errors > 0:
            logger.debug("[汇总] 全部错误详情: %s", stage_trace["errors"])

        result = SuggestionPipelineResult(
            annotation_data=final_stage.annotation_data,
            prompt_text=aggregated_prompt,
            raw_output=aggregated_raw_output,
            llm_trace=stage_trace,
            stage_records=[
                {
                    "stage": stage.stage_name,
                    "promptText": stage.prompt_text,
                    "llmTrace": stage.llm_trace,
                    "hasAnnotationData": stage.annotation_data is not None,
                }
                for stage in stage_records
            ],
        )
        logger.debug("[汇总] 最终标注数据内容: %s", final_stage.annotation_data)
        return result
