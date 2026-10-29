"""草稿生成技能：基于提示词和少样本示例生成第一版标注草稿。"""

from __future__ import annotations

import json
from typing import Any

from annotation_agent.application.ports import LLMGateway
from annotation_agent.application.suggestion.skills.common import SuggestionStageResult
from annotation_agent.contracts.suggestion_request import SuggestionRequest
from annotation_agent.domain.shared.task_context import TaskContext
from annotation_agent.infrastructure.llm.shared.prompt_messages import PromptMessages
from annotation_agent.infrastructure.llm.suggestion.output_validator import SuggestionOutputValidator
from annotation_agent.infrastructure.llm.suggestion.schema import build_suggestion_structured_schema


class DraftGenerationSkill:
    """草稿生成技能。

    流水线的第一阶段，将基础提示词、少样本示例和 Schema 摘要
    组装为完整的 prompt，调用 LLM 生成结构化的标注草稿。
    """

    def __init__(self,
                 llm_gateway: LLMGateway,
                 output_validator: SuggestionOutputValidator) -> None:
        self.llm_gateway = llm_gateway
        self.output_validator = output_validator

    def run(
        self,
        request: SuggestionRequest,
        context: TaskContext,
        base_prompt: PromptMessages,
        similar_examples: list[dict[str, Any]],
        schema_summary: dict[str, Any],
        mock_raw_output: str,
    ) -> SuggestionStageResult:
        """执行草稿生成。

        Args:
            request: 标注建议请求。
            context: 任务上下文，包含 schema 字段等信息。
            base_prompt: 基础提示词模板。
            similar_examples: 检索到的少样本示例列表。
            schema_summary: 标注 Schema 的摘要描述。
            mock_raw_output: Mock 模式下的预设原始输出。

        Returns:
            包含草稿标注数据的阶段执行结果。
        """
        prompt_messages = PromptMessages(
            system_prompt=base_prompt.system_prompt,
            user_prompt=self._build_user_prompt(
                base_user_prompt=base_prompt.user_prompt,
                similar_examples=similar_examples,
                schema_summary=schema_summary,
            ),
            user_parts=base_prompt.user_parts,
        )

        validated, raw_output, llm_trace = self.llm_gateway.invoke_generation(
            prompt=prompt_messages,
            output_validator=self.output_validator,
            validator_context=context.schema_fields,
            llm_context=context.to_llm_context(
                extra={
                    "fewShotExamples": similar_examples,
                    "schemaSummary": schema_summary,
                }
            ),
            mock_raw_output=mock_raw_output,
            structured_schema=build_suggestion_structured_schema(context.schema_fields),
        )

        return SuggestionStageResult(
            stage_name="draft_generation",
            annotation_data=None if validated is None else dict(validated["annotationData"]),
            raw_output=raw_output,
            llm_trace=llm_trace,
            prompt_text=prompt_messages.to_text(),
        )

    @staticmethod
    def _build_user_prompt(
        base_user_prompt: str,
        similar_examples: list[dict[str, Any]],
        schema_summary: dict[str, Any],
    ) -> str:
        """组装草稿生成阶段的用户提示词。

        将基础提示词、Schema 摘要和少样本示例拼接为完整的用户提示词。
        """
        sections = [
            base_user_prompt,
            "结构化 Schema 摘要:",
            json.dumps(schema_summary, ensure_ascii=False, indent=2),
        ]
        if similar_examples:
            sections.extend(
                [
                    "参考的少样本示例:",
                    json.dumps(similar_examples, ensure_ascii=False, indent=2),
                ]
            )
        return "\n\n".join(sections)
