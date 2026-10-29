"""应用层依赖契约（端口接口）。"""

from __future__ import annotations

from typing import Any, Protocol


# 审核结果仓储端口：负责审核结果的持久化与任务状态流转
class AuditResultRepository(Protocol):
    # 保存审核结果
    def save_result(self, result: Any) -> dict[str, Any]: ...
    # 根据 task_item_id 获取审核结果
    def get_result(self, task_item_id: str) -> dict[str, Any] | None: ...
    def list_results_by_task_id(self, task_id: str) -> list[dict[str, Any]]: ...
    # 根据 task_item_id 和输入哈希获取缓存的审核结果（用于幂等判断）
    def get_result_by_input_hash(self, task_item_id: str, input_hash: str) -> dict[str, Any] | None: ...
    # 保存执行追踪信息（入站事件、LLM提示词、输入哈希等）
    def save_execution_trace(
        self,
        task_item_id: str,
        task_id: str,
        inbound_event: dict[str, Any] | None = None,
        llm_prompt: str | None = None,
        input_hash: str | None = None,
    ) -> None: ...
    # 获取审核任务记录
    def get_job_record(self, task_item_id: str) -> dict[str, Any] | None: ...
    # 标记任务为待处理状态
    def mark_pending(self, task_item_id: str, task_id: str | None = None, increment_attempt: bool = False) -> dict[str, Any]: ...
    # 标记任务为处理中状态
    def mark_processing(self, task_item_id: str, task_id: str | None = None) -> dict[str, Any]: ...
    # 标记任务为已完成状态
    def mark_completed(self, task_item_id: str, result: dict[str, Any]) -> dict[str, Any]: ...
    # 标记任务为失败状态
    def mark_failed(self, task_item_id: str, error: str, task_id: str | None = None) -> dict[str, Any]: ...
    # 标记任务重试次数耗尽
    def mark_max_retries_exceeded(self, task_item_id: str, error: str, task_id: str | None = None) -> dict[str, Any]: ...


# 建议任务仓储端口：负责建议任务的创建与状态流转
class SuggestionJobRepository(Protocol):
    # 创建建议任务
    def create_job(
        self,
        suggestion_id: str,
        task_id: str,
        task_item_id: str,
        input_hash: str,
        request_payload: dict[str, Any],
    ) -> dict[str, Any]: ...
    # 获取建议任务记录
    def get_job(self, suggestion_id: str) -> dict[str, Any] | None: ...
    # 根据建议ID和任务项ID获取建议任务记录
    def get_job_for_task_item(self, suggestion_id: str, task_item_id: str) -> dict[str, Any] | None: ...
    # 根据任务项ID和输入哈希获取已完成的历史建议任务记录
    def get_completed_job_by_task_item_hash(self, task_item_id: str, input_hash: str) -> dict[str, Any] | None: ...
    # 根据输入哈希获取已完成的历史建议任务记录
    def get_completed_job_by_input_hash(self, input_hash: str) -> dict[str, Any] | None: ...
    # 标记建议任务为处理中
    def mark_processing(self, suggestion_id: str, llm_prompt: str) -> dict[str, Any] | None: ...
    # 标记建议任务为已完成
    def mark_completed(
        self,
        suggestion_id: str,
        annotation_data: dict[str, Any],
        raw_model_output: str,
        llm_prompt: str | None = None,
    ) -> dict[str, Any] | None: ...
    # 标记建议任务为失败
    def mark_failed(
        self,
        suggestion_id: str,
        error: str,
        raw_model_output: str = "",
        llm_prompt: str | None = None,
    ) -> dict[str, Any] | None: ...


# LLM 网关端口：封装对大语言模型的调用（审核与生成两种模式）
class LLMGateway(Protocol):
    # 调用 LLM 执行审核推理
    def invoke_audit(
        self,
        prompt: str,
        dimensions: list[dict[str, Any]],
        audit_context: dict[str, Any] | None = None,
        prompt_messages: Any | None = None,
    ) -> dict[str, Any] | None: ...

    # 调用 LLM 执行标注建议生成
    def invoke_generation(
        self,
        prompt: Any,
        output_validator: Any,
        validator_context: Any,
        llm_context: dict[str, Any] | None = None,
        mock_raw_output: str | None = None,
        structured_schema: dict[str, Any] | type[Any] | None = None,
    ) -> tuple[dict[str, Any] | None, str, dict[str, Any]]: ...


# 提示词模板仓储端口：根据模板ID获取提示词模板
class PromptTemplateStore(Protocol):
    def get(self,
            template_id: str) -> Any: ...
