"""Agent服务的依赖注入容器。"""

from __future__ import annotations

from concurrent.futures import Executor
from pathlib import Path
from typing import Any

from annotation_agent.app.settings import AgentSettings, load_settings
from annotation_agent.application.audit.audit_context_builder import AuditContextBuilder
from annotation_agent.application.audit.audit_execution_service import AuditExecutionService
from annotation_agent.application.audit.audit_job_service import AuditJobService
from annotation_agent.application.audit.audit_prompt_builder import AuditPromptBuilder
from annotation_agent.application.audit.audit_query_service import AuditQueryService
from annotation_agent.application.audit.media_evidence_service import MediaEvidenceService
from annotation_agent.application.shared.task_context_builder import TaskContextBuilder
from annotation_agent.application.suggestion.suggestion_command_service import SuggestionCommandService
from annotation_agent.application.suggestion.suggestion_execution_service import SuggestionExecutionService
from annotation_agent.application.suggestion.suggestion_pipeline import SuggestionPipeline
from annotation_agent.application.suggestion.suggestion_prompt_builder import SuggestionPromptBuilder
from annotation_agent.application.suggestion.suggestion_query_service import SuggestionQueryService
from annotation_agent.application.suggestion.suggestion_result_factory import SuggestionResultFactory
from annotation_agent.application.suggestion.skills.draft_generation_skill import DraftGenerationSkill
from annotation_agent.application.suggestion.skills.fewshot_retrieval_skill import FewShotRetrievalSkill
from annotation_agent.application.suggestion.skills.schema_repair_skill import SchemaRepairSkill
from annotation_agent.domain.audit.audit_result_factory import AuditResultFactory
from annotation_agent.domain.audit.decision_policy import AuditDecisionPolicy
from annotation_agent.domain.audit.rule_validator import AnnotationRuleValidator
from annotation_agent.domain.shared.task_schema import AnnotationSchemaParser
from annotation_agent.infrastructure.llm.llm_service import LLMService
from annotation_agent.infrastructure.llm.suggestion.output_validator import SuggestionOutputValidator
from annotation_agent.infrastructure.media.media_asset_discovery import MediaAssetDiscovery
from annotation_agent.infrastructure.media.media_fetcher import MediaFetcher
from annotation_agent.infrastructure.media.video_frame_extractor import VideoFrameExtractor
from annotation_agent.infrastructure.persistence.mysql.audit_result_repository import MySQLAuditResultRepository
from annotation_agent.infrastructure.persistence.mysql.connection import MySQLConnectionFactory
from annotation_agent.infrastructure.persistence.mysql.migration_runner import MySQLMigrationRunner
from annotation_agent.infrastructure.persistence.mysql.suggestion_job_repository import MySQLSuggestionJobRepository
from annotation_agent.infrastructure.prompts.prompt_template_repository import PromptTemplateRepository
from annotation_agent.infrastructure.retrieval.example_store import SuggestionExampleStore
from annotation_agent.infrastructure.retrieval.suggestion_example_retriever import SuggestionExampleRetriever
from annotation_agent.infrastructure.tools.suggestion_tools import SuggestionTools
from annotation_agent.interfaces.messaging.rabbitmq_audit_consumer import RabbitMQAuditConsumer


class ServiceContainer:
    """集中管理服务依赖的单例容器，支持外部覆盖注入。"""

    def __init__(
        self,
        settings: AgentSettings | None = None,
        audit_repository: Any | None = None,
        suggestion_repository: Any | None = None,
        llm_gateway: Any | None = None,
        suggestion_executor: Executor | None = None,
        suggestion_run_async: bool = True,
    ) -> None:
        self.settings = settings or load_settings()
        self._audit_repository_override = audit_repository
        self._suggestion_repository_override = suggestion_repository
        self._llm_gateway_override = llm_gateway
        self._suggestion_executor_override = suggestion_executor
        self._suggestion_run_async = suggestion_run_async
        self._instances: dict[str, Any] = {}
        self._migrations_ran = False

    @property
    def mysql_connection_factory(self) -> MySQLConnectionFactory:
        return self._get_or_create(
            "mysql_connection_factory",
            lambda: MySQLConnectionFactory(self.settings.mysql),
        )

    @property
    def llm_gateway(self) -> Any:
        return self._get_or_create(
            "llm_gateway",
            lambda: self._llm_gateway_override or LLMService(self.settings.llm),
        )

    @property
    def audit_repository(self) -> Any:
        if self._audit_repository_override is None:
            self.run_migrations()
        return self._get_or_create(
            "audit_repository",
            lambda: self._audit_repository_override or MySQLAuditResultRepository(self.mysql_connection_factory),
        )

    @property
    def suggestion_repository(self) -> Any:
        if self._suggestion_repository_override is None:
            self.run_migrations()
        return self._get_or_create(
            "suggestion_repository",
            lambda: self._suggestion_repository_override or MySQLSuggestionJobRepository(self.mysql_connection_factory),
        )

    @property
    def prompt_template_repository(self) -> PromptTemplateRepository:
        return self._get_or_create("prompt_template_repository", PromptTemplateRepository)

    @property
    def audit_context_builder(self) -> AuditContextBuilder:
        return self._get_or_create(
            "audit_context_builder",
            lambda: AuditContextBuilder(
                prompt_builder=AuditPromptBuilder(),
                schema_parser=AnnotationSchemaParser(),
                annotation_validator=AnnotationRuleValidator(),
                prompt_template_repository=self.prompt_template_repository,
                media_evidence_service=self.media_evidence_service,
            ),
        )

    @property
    def media_evidence_service(self) -> MediaEvidenceService:
        return self._get_or_create(
            "media_evidence_service",
            lambda: MediaEvidenceService(
                settings=self.settings.media,
                discovery=MediaAssetDiscovery(),
                media_fetcher=MediaFetcher(),
                video_frame_extractor=VideoFrameExtractor(),
            ),
        )

    @property
    def audit_execution_service(self) -> AuditExecutionService:
        return self._get_or_create(
            "audit_execution_service",
            lambda: AuditExecutionService(
                audit_repository=self.audit_repository,
                llm_gateway=self.llm_gateway,
                context_builder=self.audit_context_builder,
                decision_policy=AuditDecisionPolicy(),
                result_factory=AuditResultFactory(),
            ),
        )

    @property
    def audit_job_service(self) -> AuditJobService:
        return self._get_or_create(
            "audit_job_service",
            lambda: AuditJobService(
                audit_execution_service=self.audit_execution_service,
                audit_repository=self.audit_repository,
                rabbitmq_settings=self.settings.rabbitmq,
            ),
        )

    @property
    def audit_query_service(self) -> AuditQueryService:
        return self._get_or_create(
            "audit_query_service",
            lambda: AuditQueryService(self.audit_repository),
        )

    @property
    def task_context_builder(self) -> TaskContextBuilder:
        return self._get_or_create(
            "task_context_builder",
            lambda: TaskContextBuilder(schema_parser=AnnotationSchemaParser()),
        )

    @property
    def suggestion_execution_service(self) -> SuggestionExecutionService:
        return self._get_or_create(
            "suggestion_execution_service",
            lambda: SuggestionExecutionService(
                suggestion_repository=self.suggestion_repository,
                llm_gateway=self.llm_gateway,
                task_context_builder=self.task_context_builder,
                prompt_builder=SuggestionPromptBuilder(self.prompt_template_repository),
                output_validator=SuggestionOutputValidator(),
                pipeline=self.suggestion_pipeline,
            ),
        )

    @property
    def suggestion_example_store(self) -> SuggestionExampleStore:
        return self._get_or_create(
            "suggestion_example_store",
            lambda: SuggestionExampleStore(self.settings.suggestion.examples_path),
        )

    @property
    def suggestion_example_retriever(self) -> SuggestionExampleRetriever:
        return self._get_or_create(
            "suggestion_example_retriever",
            lambda: SuggestionExampleRetriever(
                store=self.suggestion_example_store,
                max_examples=self.settings.suggestion.max_examples,
            ),
        )

    @property
    def suggestion_tools(self) -> SuggestionTools:
        return self._get_or_create(
            "suggestion_tools",
            lambda: SuggestionTools(
                retriever=self.suggestion_example_retriever,
            ),
        )

    @property
    def suggestion_pipeline(self) -> SuggestionPipeline:
        return self._get_or_create(
            "suggestion_pipeline",
            lambda: SuggestionPipeline(
                tools=self.suggestion_tools,
                fewshot_skill=FewShotRetrievalSkill(self.suggestion_tools),
                draft_skill=DraftGenerationSkill(self.llm_gateway, SuggestionOutputValidator()),
                schema_repair_skill=SchemaRepairSkill(self.llm_gateway, SuggestionOutputValidator()),
            ),
        )

    @property
    def suggestion_command_service(self) -> SuggestionCommandService:
        return self._get_or_create(
            "suggestion_command_service",
            lambda: SuggestionCommandService(
                suggestion_repository=self.suggestion_repository,
                task_context_builder=self.task_context_builder,
                execution_service=self.suggestion_execution_service,
                result_factory=SuggestionResultFactory(),
                executor=self._suggestion_executor_override,
                run_async=self._suggestion_run_async,
            ),
        )

    @property
    def suggestion_query_service(self) -> SuggestionQueryService:
        return self._get_or_create(
            "suggestion_query_service",
            lambda: SuggestionQueryService(
                suggestion_repository=self.suggestion_repository,
                result_factory=SuggestionResultFactory(),
            ),
        )

    @property
    def rabbitmq_audit_consumer(self) -> RabbitMQAuditConsumer:
        return self._get_or_create(
            "rabbitmq_audit_consumer",
            lambda: RabbitMQAuditConsumer(
                rabbitmq_settings=self.settings.rabbitmq,
                audit_job_service=self.audit_job_service,
            ),
        )

    def run_migrations(self) -> None:
        if self._migrations_ran:
            return
        migrations_dir = Path(__file__).resolve().parents[1] / "infrastructure" / "persistence" / "mysql" / "migrations"
        MySQLMigrationRunner(self.mysql_connection_factory, migrations_dir).run()
        self._migrations_ran = True

    def readiness_report(self) -> dict[str, Any]:
        checks = {
            "mysql": self._check_mysql(),
            "llm": self._check_llm_config(),
            "rabbitmq": {"ready": True, "enabled": self.settings.rabbitmq.enabled},
        }
        return {
            "ready": all(check["ready"] for check in checks.values()),
            "checks": checks,
        }

    def _check_mysql(self) -> dict[str, Any]:
        try:
            with self.mysql_connection_factory.connect() as connection:
                with connection.cursor() as cursor:
                    cursor.execute("SELECT 1 AS ok")
                    cursor.fetchone()
            return {"ready": True}
        except Exception as exc:
            return {"ready": False, "error": str(exc)}

    def _check_llm_config(self) -> dict[str, Any]:
        if self.settings.llm.use_mock or self.settings.llm.provider == "mock":
            return {"ready": True, "mode": "mock"}
        return {
            "ready": bool(self.settings.llm.api_key),
            "mode": self.settings.llm.provider,
            "error": "" if self.settings.llm.api_key else "AI_AUDIT_API_KEY is not configured",
        }

    def _get_or_create(self, key: str, factory):
        if key not in self._instances:
            self._instances[key] = factory()
        return self._instances[key]
