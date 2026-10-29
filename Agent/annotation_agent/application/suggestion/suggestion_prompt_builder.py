"""建议提示词构建器。"""

from __future__ import annotations

import json

from annotation_agent.application.shared.prompt_injection_guard import PromptInjectionGuard

from annotation_agent.contracts.suggestion_request import SuggestionRequest
from annotation_agent.domain.shared.task_context import TaskContext
from annotation_agent.infrastructure.llm.shared.langchain_prompt_adapter import render_prompt_messages
from annotation_agent.infrastructure.llm.shared.prompt_messages import PromptMessages
from annotation_agent.infrastructure.prompts.prompt_template_repository import PromptTemplateRepository


# 建议提示词构建器：将建议请求与任务上下文组装为系统提示词和用户提示词
class SuggestionPromptBuilder:
    _PLACEHOLDERS = (
        "{task_id}",
        "{task_item_id}",
        "{task_title}",
        "{task_description}",
        "{raw_data}",
        "{annotation_schema}",
        "{writable_fields}",
    )

    def __init__(self,
                 prompt_template_repository: PromptTemplateRepository | None = None) -> None:
        self.prompt_template_repository = prompt_template_repository or PromptTemplateRepository()

    # 构建建议提示词消息：组装任务信息与标注 schema 到模板中
    def build(self,
              request: SuggestionRequest,
              context: TaskContext) -> PromptMessages:
        system_template = self.prompt_template_repository.get("suggestion_system").content
        user_template = self.prompt_template_repository.get("suggestion_user").content

        guard = PromptInjectionGuard()
        def clean(value, field):
            return guard.sanitize_payload(value, source="untrusted_data", field_path=field).sanitized_value

        prompt_messages = render_prompt_messages(
            system_template=system_template,
            user_template=user_template,
            variables={
                "task_id": request.task_id,
                "task_item_id": request.task_item_id,
                "task_title": clean(request.task_title, "taskTitle"),
                "task_description": clean(request.task_description, "taskDescription"),
                "raw_data": self._to_text(clean(request.raw_data, "rawData")),
                "annotation_schema": self._to_text([field.to_dict() for field in context.schema_fields]),
                "writable_fields": self._to_text([
                    field.field
                    for field in context.schema_fields
                ]),
            },
        )

        self._assert_no_unresolved_placeholders(prompt_messages.system_prompt)
        self._assert_no_unresolved_placeholders(prompt_messages.user_prompt)
        return prompt_messages

    def _assert_no_unresolved_placeholders(self, prompt_text: str) -> None:
        unresolved = [
            placeholder
            for placeholder in self._PLACEHOLDERS
            if placeholder in prompt_text
        ]
        if unresolved:
            raise ValueError(
                "prompt template contains unresolved placeholders: "
                + ", ".join(unresolved)
            )

    @staticmethod
    def _to_text(value: dict | list | str) -> str:
        if isinstance(value, str):
            return value
        return json.dumps(value, ensure_ascii=False, indent=2)
