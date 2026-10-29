"""建议提交与分发服务。"""

from __future__ import annotations

import logging
from concurrent.futures import Executor, ThreadPoolExecutor
from time import perf_counter
from typing import Any

from annotation_agent.application.ports import SuggestionJobRepository
from annotation_agent.application.shared.task_context_builder import TaskContextBuilder
from annotation_agent.application.shared.flow_logging import elapsed_ms, summarize_payload
from annotation_agent.contracts.suggestion_request import SuggestionRequest
from annotation_agent.application.suggestion.suggestion_execution_service import SuggestionExecutionService
from annotation_agent.application.suggestion.suggestion_result_factory import SuggestionResultFactory

logger = logging.getLogger(__name__)

# 默认线程池执行器：用于异步分发建议任务
DEFAULT_SUGGESTION_EXECUTOR = ThreadPoolExecutor(max_workers=4,
                                                 thread_name_prefix="suggestion-worker")


# 建议命令服务：负责建议的提交、重试与异步分发
class SuggestionCommandService:
    def __init__(
        self,
        suggestion_repository: SuggestionJobRepository,
        task_context_builder: TaskContextBuilder,
        execution_service: SuggestionExecutionService,
        result_factory: SuggestionResultFactory,
        executor: Executor | None = None,
        run_async: bool = True,
    ) -> None:
        self.suggestion_repository = suggestion_repository
        self.task_context_builder = task_context_builder
        self.execution_service = execution_service
        self.result_factory = result_factory
        self.executor = executor or DEFAULT_SUGGESTION_EXECUTOR
        self.run_async = run_async

    # 提交建议请求：构建上下文、创建任务记录、分发执行
    def submit(self,
               payload: dict[str, Any] | None = None) -> dict[str, Any]:
        started_at = perf_counter()
        payload = payload or {}
        logger.info(
            "【建议提交】收到请求: task_id=%s, task_item_id=%s, 输入摘要=%s",
            payload.get("taskId", ""),
            payload.get("taskItemId", ""),
            summarize_payload(payload, keys=("taskId", "taskItemId", "taskTitle", "materials", "rawData")),
        )
        request = SuggestionRequest.from_payload(payload)
        context = self.task_context_builder.build(request)
        logger.info(
            "【建议提交】上下文完成: task_id=%s, task_item_id=%s, schema字段=%s个, input_hash=%s",
            request.task_id,
            request.task_item_id,
            len(context.schema_fields),
            context.input_hash[:12],
        )

        cached_job = self.suggestion_repository.get_completed_job_by_task_item_hash(
            request.task_item_id,
            context.input_hash,
        )
        if cached_job:
            logger.info(
                "【建议提交】命中历史结果: suggestion_id=%s, task_id=%s, task_item_id=%s, input_hash=%s, 耗时=%sms",
                cached_job.get("suggestionId", ""),
                request.task_id,
                request.task_item_id,
                context.input_hash[:12],
                elapsed_ms(started_at, perf_counter()),
            )
            return self.result_factory.build_submission_result(cached_job)

        cached_job = self.suggestion_repository.get_completed_job_by_input_hash(context.input_hash)
        if cached_job:
            cloned_job = self._clone_cached_job_for_current_request(request, context.input_hash, cached_job)
            logger.info(
                "【建议提交】命中跨任务项历史结果: source_suggestion_id=%s, suggestion_id=%s, "
                "task_id=%s, task_item_id=%s, input_hash=%s, 耗时=%sms",
                cached_job.get("suggestionId", ""),
                cloned_job.get("suggestionId", ""),
                request.task_id,
                request.task_item_id,
                context.input_hash[:12],
                elapsed_ms(started_at, perf_counter()),
            )
            return self.result_factory.build_submission_result(cloned_job)

        # 生成唯一建议ID
        suggestion_id = self.result_factory.new_suggestion_id()

        # 在仓储中创建建议任务记录
        job_record = self.suggestion_repository.create_job(
            suggestion_id=suggestion_id,
            task_id=request.task_id,
            task_item_id=request.task_item_id,
            input_hash=context.input_hash,
            request_payload=request.to_payload(),
        )
        logger.info(
            "【建议提交】任务已创建: suggestion_id=%s, task_id=%s, task_item_id=%s, 状态=%s",
            suggestion_id,
            request.task_id,
            request.task_item_id,
            job_record.get("status", ""),
        )

        # 分发任务到执行器
        self._dispatch_job(suggestion_id)
        logger.info(
            "【建议提交】任务已分发: suggestion_id=%s, 执行方式=%s, 接收耗时=%sms",
            suggestion_id,
            "异步" if self.run_async else "同步",
            elapsed_ms(started_at, perf_counter()),
        )
        return self.result_factory.build_submission_result(job_record)

    def _clone_cached_job_for_current_request(
        self,
        request: SuggestionRequest,
        input_hash: str,
        cached_job: dict[str, Any],
    ) -> dict[str, Any]:
        suggestion_id = self.result_factory.new_suggestion_id()
        self.suggestion_repository.create_job(
            suggestion_id=suggestion_id,
            task_id=request.task_id,
            task_item_id=request.task_item_id,
            input_hash=input_hash,
            request_payload=request.to_payload(),
        )
        return self.suggestion_repository.mark_completed(
            suggestion_id,
            cached_job.get("annotationData") or {},
            cached_job.get("rawModelOutput") or "",
            cached_job.get("llmPrompt") or "",
        ) or {
            "suggestionId": suggestion_id,
            "taskId": request.task_id,
            "taskItemId": request.task_item_id,
            "status": "completed",
            "annotationData": cached_job.get("annotationData") or {},
            "lastError": "",
        }

    # 重试建议请求：委托给 submit 方法
    def retry(self,
              payload: dict[str, Any] | None = None) -> dict[str, Any]:
        return self.submit(payload)

    # 分发建议任务：根据配置选择同步或异步执行
    def _dispatch_job(self,
                      suggestion_id: str) -> None:
        if self.run_async:
            # 异步：提交到线程池
            self.executor.submit(self._run_job_safely,
                                 suggestion_id)
            return
        # 同步：直接执行
        self._run_job_safely(suggestion_id)

    # 安全执行建议任务：捕获所有异常防止线程池泄漏
    def _run_job_safely(self,
                        suggestion_id: str) -> None:
        try:
            self.execution_service.execute(suggestion_id)
        except Exception:
            logger.exception("建议任务异常失败: suggestion_id=%s", suggestion_id)
