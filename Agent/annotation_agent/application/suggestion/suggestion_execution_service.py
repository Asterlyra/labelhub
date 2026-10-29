"""建议执行服务。"""

from __future__ import annotations

import json
import logging
from time import perf_counter
from typing import Any

from annotation_agent.application.ports import LLMGateway, SuggestionJobRepository
from annotation_agent.application.shared.flow_logging import elapsed_ms, summarize_payload
from annotation_agent.application.shared.task_context_builder import TaskContextBuilder
from annotation_agent.application.suggestion.suggestion_pipeline import SuggestionPipeline
from annotation_agent.application.suggestion.suggestion_prompt_builder import SuggestionPromptBuilder
from annotation_agent.contracts.suggestion_request import SuggestionRequest
from annotation_agent.domain.shared.task_context import TaskContext
from annotation_agent.domain.shared.task_schema import SchemaField
from annotation_agent.infrastructure.llm.suggestion.output_validator import SuggestionOutputValidator

logger = logging.getLogger(__name__)


# 建议执行服务：负责单个建议任务的完整执行流程（构建提示词 -> 调用 LLM -> 保存结果）
class SuggestionExecutionService:
    def __init__(
        self,
        suggestion_repository: SuggestionJobRepository,
        llm_gateway: LLMGateway,
        task_context_builder: TaskContextBuilder,
        prompt_builder: SuggestionPromptBuilder,
        output_validator: SuggestionOutputValidator,
        pipeline: SuggestionPipeline,
    ) -> None:
        self.suggestion_repository = suggestion_repository
        self.llm_gateway = llm_gateway
        self.task_context_builder = task_context_builder
        self.prompt_builder = prompt_builder
        self.output_validator = output_validator
        self.pipeline = pipeline

    # 执行建议任务：查询任务记录、构建提示词、调用 LLM 生成标注建议
    def execute(self, suggestion_id: str) -> dict[str, Any] | None:
        started_at = perf_counter()
        logger.info("【建议执行】开始: suggestion_id=%s", suggestion_id)
        # 查询建议任务记录
        suggestion_job = self.suggestion_repository.get_job(suggestion_id)

        if not suggestion_job:
            logger.warning("【建议执行】任务未找到: suggestion_id=%s, 耗时=%sms", suggestion_id, elapsed_ms(started_at, perf_counter()))
            return None
        # 已完成的任务直接返回
        if suggestion_job.get("status") == "completed":
            logger.info(
                "【建议执行】任务已完成，直接返回: suggestion_id=%s, task_id=%s, task_item_id=%s, 耗时=%sms",
                suggestion_id,
                suggestion_job.get("taskId", ""),
                suggestion_job.get("taskItemId", ""),
                elapsed_ms(started_at, perf_counter()),
            )
            return suggestion_job

        # 从任务记录中重建请求与上下文
        logger.info(
            "【建议执行】载入任务: suggestion_id=%s, task_id=%s, task_item_id=%s, 状态=%s, 输入摘要=%s",
            suggestion_id,
            suggestion_job.get("taskId", ""),
            suggestion_job.get("taskItemId", ""),
            suggestion_job.get("status", ""),
            summarize_payload(suggestion_job.get("requestPayload"), keys=("taskId", "taskItemId", "materials", "rawData")),
        )
        request = SuggestionRequest.from_payload(suggestion_job.get("requestPayload"))
        context = self.task_context_builder.build(request)
        # 构建建议提示词（system + user）
        prompt_messages = self.prompt_builder.build(request, context)
        logger.info(
            "【建议执行】提示词完成: suggestion_id=%s, schema字段=%s个, prompt长度=%s, input_hash=%s",
            suggestion_id,
            len(context.schema_fields),
            len(prompt_messages.to_text()),
            context.input_hash[:12],
        )
        # 标记任务为处理中
        self.suggestion_repository.mark_processing(suggestion_id, prompt_messages.to_text())
        logger.info("【建议执行】状态更新: suggestion_id=%s, 状态=processing", suggestion_id)

        # 调用 LLM 网关执行建议生成
        llm_started_at = perf_counter()
        logger.info(
            "【建议执行】调用LLM: suggestion_id=%s, task_id=%s, task_item_id=%s",
            suggestion_id,
            request.task_id,
            request.task_item_id,
        )
        pipeline_result = self.pipeline.run(
            request=request,
            context=context,
            base_prompt=prompt_messages,
            mock_raw_output=self._build_mock_raw_output(context),
        )

        model_result = None if pipeline_result.annotation_data is None \
                            else {"annotationData": pipeline_result.annotation_data}

        raw_model_output = pipeline_result.raw_output
        llm_trace = pipeline_result.llm_trace

        logger.info(
            "【建议执行】LLM返回: suggestion_id=%s, 是否成功=%s, 原始输出长度=%s, 耗时=%sms",
            suggestion_id,
            model_result is not None,
            len(raw_model_output or ""),
            elapsed_ms(llm_started_at, perf_counter()),
        )

        # LLM 返回空结果时标记失败
        if model_result is None:
            failed_job = self.suggestion_repository.mark_failed(
                suggestion_id,
                self._resolve_error(llm_trace),
                raw_model_output,
                pipeline_result.prompt_text,
            )
            logger.info(
                "【建议执行】失败: suggestion_id=%s, 状态=failed, 错误=%s, 总耗时=%sms",
                suggestion_id,
                (failed_job or {}).get("lastError", ""),
                elapsed_ms(started_at, perf_counter()),
            )
            return failed_job

        # 保存生成的标注数据
        completed_job = self.suggestion_repository.mark_completed(
            suggestion_id,
            model_result["annotationData"],
            self._serialize_completed_raw_output(model_result["annotationData"]),
            pipeline_result.prompt_text,
        )

        logger.info(
            "【建议执行】完成: suggestion_id=%s, task_id=%s, task_item_id=%s, 输出摘要=%s, 总耗时=%sms",
            suggestion_id,
            request.task_id,
            request.task_item_id,
            summarize_payload(model_result, keys=("annotationData",)),
            elapsed_ms(started_at, perf_counter()),
        )
        return completed_job

    # 从 LLM 追踪信息中提取错误描述
    @staticmethod
    def _resolve_error(llm_trace: dict[str, Any]) -> str:
        errors = llm_trace.get("errors") or []
        return str(errors[-1]) if errors else "AI建议生成失败"

    # 构建模拟输出用于本地验证（不调用 LLM）
    def _build_mock_raw_output(self, context: TaskContext) -> str:
        annotation_data = {}
        for field in context.schema_fields:
            annotation_data[field.field] = self._build_mock_value(field)
        return json.dumps({"annotationData": annotation_data}, ensure_ascii=False)

    @staticmethod
    def _serialize_completed_raw_output(annotation_data: dict[str, Any]) -> str:
        return json.dumps({"annotationData": annotation_data}, ensure_ascii=False)

    # 根据字段类型生成模拟值
    @staticmethod
    def _build_mock_value(field: SchemaField) -> Any:
        # 单选/下拉/评分类型：取第一个选项
        if field.type in {"radio", "select", "rating"} and field.options:
            return field.options[0]
        # 多选类型：取前一个选项作为列表
        if field.type in {"checkbox", "tagSelect", "multiSelect"}:
            return field.options[:1] if field.options else []
        # 其他类型：使用占位文本
        return f"{field.label}的建议值"
                                      
