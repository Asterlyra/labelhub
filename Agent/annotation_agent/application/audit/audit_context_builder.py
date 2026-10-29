"""审核上下文组装。"""

from __future__ import annotations

from annotation_agent.application.audit.audit_prompt_builder import AuditPromptBuilder
from annotation_agent.application.shared.prompt_injection_guard import PromptInjectionGuard
from annotation_agent.application.audit.media_evidence_service import MediaEvidenceService
from annotation_agent.application.shared.idempotency_service import IdempotencyService
from annotation_agent.application.shared.task_context_builder import TaskContextBuilder
from annotation_agent.contracts.audit_request import AuditRequest
from annotation_agent.domain.audit.audit_config import AuditConfig
from annotation_agent.domain.audit.audit_context import AuditContext, EvidenceBundle
from annotation_agent.domain.audit.rule_validator import AnnotationRuleValidator
from annotation_agent.domain.shared.task_schema import AnnotationSchemaParser
from annotation_agent.infrastructure.prompts.prompt_template_repository import PromptTemplateRepository


# 审核上下文构建器：将审核请求组装为包含完整上下文信息的 AuditContext
class AuditContextBuilder:
    def __init__(
        self,
        prompt_builder: AuditPromptBuilder,
        schema_parser: AnnotationSchemaParser,
        annotation_validator: AnnotationRuleValidator,
        prompt_template_repository: PromptTemplateRepository | None = None,
        idempotency_service: IdempotencyService | None = None,
        media_evidence_service: MediaEvidenceService | None = None,
    ) -> None:
        self.prompt_builder = prompt_builder
        self.schema_parser = schema_parser
        self.annotation_validator = annotation_validator
        self.prompt_template_repository = prompt_template_repository or PromptTemplateRepository()
        self.idempotency_service = idempotency_service or IdempotencyService()
        self.media_evidence_service = media_evidence_service
        # 复用共享的任务上下文构建器
        self.shared_context_builder = TaskContextBuilder(
            schema_parser=self.schema_parser,
            idempotency_service=self.idempotency_service,
        )

    # 构建完整的审核上下文：任务上下文、规则校验、配置解析、提示词渲染、证据包组装
    def build(self, request: AuditRequest) -> AuditContext:
        # 构建共享的任务上下文（包含 schema 解析、输入哈希等）
        shared_context = self.shared_context_builder.build(request)
        # 执行确定性规则校验，获取校验问题列表
        validation_issues = self.annotation_validator.validate(
            shared_context.schema_fields,
            request.annotation_result,
        )
        # 从请求中解析审核配置，并检查可配置指令。
        guard = PromptInjectionGuard()
        checked = guard.validate_config_text(str((request.review_config or {}).get("reviewPrompt") or ""), source="config", field_path="reviewConfig.reviewPrompt")
        if checked.blocked:
            raise ValueError(checked.reason)
        def clean(value, field):
            return guard.sanitize_payload(value, source="untrusted_data", field_path=field).sanitized_value
        config = self._resolve_config(request)
        prepared_media_evidence = self.media_evidence_service.prepare(  # type: ignore[union-attr]
            request.raw_data,
            request.annotation_schema,
        ) if self.media_evidence_service is not None else None
        # 渲染提示词消息（系统提示词 + 用户提示词）
        prompt_messages = self.prompt_builder.render_messages(
            config=config,
            raw_data=clean(request.raw_data, "rawData"),
            annotation_result=clean(request.annotation_result, "annotationResult"),
            annotation_schema=[field.to_dict() for field in shared_context.schema_fields],
            task_title=clean(request.task_title, "taskTitle"),
            task_description=clean(request.task_description, "taskDescription"),
            review_config=request.review_config,
            validation_issues=validation_issues,
            system_template=self.prompt_template_repository.get("audit_system").content,
            user_template=self.prompt_template_repository.get("audit_user").content,
            media_prompt_section=prepared_media_evidence.to_prompt_section() if prepared_media_evidence else "",
            media_evidence=prepared_media_evidence,
        )
        return AuditContext(
            request=request,
            config=config,
            schema_fields=shared_context.schema_fields,
            validation_issues=validation_issues,
            evidence_bundle=self._build_evidence_bundle(validation_issues),
            prepared_media_evidence=prepared_media_evidence,
            prompt_messages=prompt_messages,
            prompt=prompt_messages.to_text(),
            input_hash=shared_context.input_hash,
        )

    # 从请求中解析并返回审核配置
    @staticmethod
    def _resolve_config(request: AuditRequest) -> AuditConfig:
        if not request.review_config:
            raise ValueError("reviewConfig is required")
        return AuditConfig.from_dict(request.review_config)

    # 构建证据包，记录确定性校验发现的问题数量
    @staticmethod
    def _build_evidence_bundle(validation_issues: list[dict[str, object]]) -> EvidenceBundle:
        return EvidenceBundle(
            task_kind="backend_payload_passthrough",
            items=[],
            summary=(
                "The audit prompt is built directly from the backend request payload. "
                f"Deterministic validation found {len(validation_issues)} issues before model execution."
            ),
        )
