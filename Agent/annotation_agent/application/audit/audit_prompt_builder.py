"""审核提示词渲染。"""

from __future__ import annotations

import json

from annotation_agent.domain.audit.audit_config import AuditConfig
from annotation_agent.domain.audit.media_models import PreparedMediaEvidence
from annotation_agent.infrastructure.llm.shared.langchain_prompt_adapter import render_prompt_messages
from annotation_agent.infrastructure.llm.shared.prompt_messages import PromptContentPart, PromptMessages


# 审核提示词构建器：负责将审核上下文数据填充到提示词模板中
class AuditPromptBuilder:
    # 所有支持的占位符列表，用于最终检查是否全部被替换
    _PLACEHOLDERS = (
        "{review_prompt}",
        "{raw_data}",
        "{task_title}",
        "{task_description}",
        "{annotation_schema}",
        "{annotation_result}",
        "{review_config}",
        "{validation_issues}",
        "{dimension_coverage_rule}",
        "{evidence}",
    )

    # 渲染系统提示词和用户提示词，返回结构化的 PromptMessages
    def render_messages(
        self,
        config: AuditConfig,
        raw_data: dict | str,
        annotation_result: dict | str,
        annotation_schema: dict | list | str | None = None,
        task_title: str | None = None,
        task_description: str | None = None,
        review_config: dict | list | str | None = None,
        validation_issues: list | None = None,
        system_template: str | None = None,
        user_template: str | None = None,
        media_prompt_section: str = "",
        media_evidence: PreparedMediaEvidence | None = None,
    ) -> PromptMessages:
        if system_template is None or user_template is None:
            raise ValueError("system_template and user_template are required for audit messages")

        placeholder_values = self._build_placeholder_values(
            config,
            raw_data,
            annotation_result,
            annotation_schema,
            task_title,
            task_description,
            review_config,
            validation_issues,
        )
        prompt_messages = render_prompt_messages(
            system_template=system_template,
            user_template=user_template,
            variables={
                "review_prompt": placeholder_values["{review_prompt}"],
                "raw_data": placeholder_values["{raw_data}"],
                "task_title": placeholder_values["{task_title}"],
                "task_description": placeholder_values["{task_description}"],
                "annotation_schema": placeholder_values["{annotation_schema}"],
                "annotation_result": placeholder_values["{annotation_result}"],
                "review_config": placeholder_values["{review_config}"],
                "validation_issues": placeholder_values["{validation_issues}"],
                "dimension_coverage_rule": placeholder_values["{dimension_coverage_rule}"],
                "evidence": "",
            },
            user_parts=tuple(self._build_media_parts(media_evidence)),
        )
        system_prompt = prompt_messages.system_prompt
        user_prompt = prompt_messages.user_prompt
        if media_prompt_section:
            user_prompt = f"{user_prompt}\n\n多媒体审核补充信息：\n{media_prompt_section}"
        self._assert_no_unresolved_placeholders(system_prompt)
        self._assert_no_unresolved_placeholders(user_prompt)
        return PromptMessages(
            system_prompt=system_prompt,
            user_prompt=user_prompt,
            user_parts=prompt_messages.user_parts,
        )

    # 构建所有占位符到实际值的映射
    def _build_placeholder_values(
        self,
        config: AuditConfig,
        raw_data: dict | str,
        annotation_result: dict | str,
        annotation_schema: dict | list | str | None = None,
        task_title: str | None = None,
        task_description: str | None = None,
        review_config: dict | list | str | None = None,
        validation_issues: list | None = None,
    ) -> dict[str, str]:
        # 审核提示词优先使用 review_config 中的覆盖值
        review_prompt = config.review_prompt
        if isinstance(review_config, dict):
            review_prompt = str(review_config.get("reviewPrompt") or review_prompt)

        return {
            "{review_prompt}": review_prompt,
            "{raw_data}": self._to_text(raw_data),
            "{task_title}": task_title or "",
            "{task_description}": task_description or "",
            "{annotation_schema}": self._to_text(annotation_schema or {}),
            "{annotation_result}": self._to_text(annotation_result),
            "{review_config}": self._to_text(review_config or {}),
            "{validation_issues}": self._to_text(validation_issues or []),
            "{dimension_coverage_rule}": self._build_dimension_coverage_rule(config),
        }

    # 构建审核维度覆盖规则文本，要求 LLM 对每个维度恰好打分一次
    @staticmethod
    def _build_dimension_coverage_rule(config: AuditConfig) -> str:
        dimension_fields = [dimension.field for dimension in config.dimensions if dimension.field]
        if not dimension_fields:
            raise ValueError("audit config requires at least one dimension field")
        rendered_fields = ", ".join(dimension_fields)
        return (
            f"请对以下每个审核维度恰好评分一次：{rendered_fields}。\n"
            "scores数组必须覆盖所有列出的维度，不能遗漏，也不能添加额外维度。"
        )

    # 检查提示词中是否仍有未替换的占位符
    def _assert_no_unresolved_placeholders(self, prompt_text: str) -> None:
        unresolved = [placeholder
                      for placeholder in self._PLACEHOLDERS
                      if placeholder in prompt_text]
        if unresolved:
            raise ValueError(f"prompt template contains unresolved placeholders: "
                             f"{', '.join(unresolved)}")

    @staticmethod
    def _build_media_parts(media_evidence: PreparedMediaEvidence | None) -> list[PromptContentPart]:
        if not media_evidence or not media_evidence.evidence_used:
            return []
        return [
            PromptContentPart.image_url_part(item.locator)
            for item in media_evidence.evidence_used
            if item.locator
        ]

    # 将非字符串值序列化为 JSON 文本
    @staticmethod
    def _to_text(value: dict | list | str) -> str:
        if isinstance(value, str):
            return value
        return json.dumps(value, ensure_ascii=False, indent=2)
