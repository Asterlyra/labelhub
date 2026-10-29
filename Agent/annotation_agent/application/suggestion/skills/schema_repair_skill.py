"""Schema repair skill for invalid suggestion outputs."""

from __future__ import annotations

import json

from annotation_agent.application.ports import LLMGateway
from annotation_agent.application.suggestion.skills.common import SuggestionStageResult
from annotation_agent.domain.shared.task_context import TaskContext
from annotation_agent.infrastructure.llm.shared.prompt_messages import PromptMessages
from annotation_agent.infrastructure.llm.suggestion.output_validator import SuggestionOutputValidator
from annotation_agent.infrastructure.llm.suggestion.schema import build_suggestion_structured_schema


class SchemaRepairSkill:
    """Repair invalid draft outputs so they satisfy the annotation schema."""

    def __init__(
        self,
        llm_gateway: LLMGateway,
        output_validator: SuggestionOutputValidator,
    ) -> None:
        self.llm_gateway = llm_gateway
        self.output_validator = output_validator

    def run(
        self,
        context: TaskContext,
        base_prompt: PromptMessages,
        invalid_raw_output: str,
        errors: list[str],
        schema_summary: dict[str, object],
        mock_raw_output: str,
    ) -> SuggestionStageResult:
        prompt_messages = PromptMessages(
            system_prompt=base_prompt.system_prompt,
            user_prompt=self._build_user_prompt(
                base_user_prompt=base_prompt.user_prompt,
                invalid_raw_output=invalid_raw_output,
                errors=errors,
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
                    "schemaSummary": schema_summary,
                    "invalidRawOutput": invalid_raw_output,
                    "repairErrors": errors,
                }
            ),
            mock_raw_output=mock_raw_output,
            structured_schema=build_suggestion_structured_schema(context.schema_fields),
        )
        return SuggestionStageResult(
            stage_name="schema_repair",
            annotation_data=None if validated is None else dict(validated["annotationData"]),
            raw_output=raw_output,
            llm_trace=llm_trace,
            prompt_text=prompt_messages.to_text(),
        )

    @staticmethod
    def _build_user_prompt(
        base_user_prompt: str,
        invalid_raw_output: str,
        errors: list[str],
        schema_summary: dict[str, object],
    ) -> str:
        rendered_errors = "\n".join(f"- {error}" for error in errors) if errors else "- Structured output is invalid."
        return "\n\n".join(
            [
                base_user_prompt,
                "Previous invalid model output:",
                invalid_raw_output or "<empty>",
                "Validation errors:",
                rendered_errors,
                "Schema summary:",
                json.dumps(schema_summary, ensure_ascii=False, indent=2),
                "Repair the output into a valid annotationData object that strictly follows the schema.",
            ]
        )
