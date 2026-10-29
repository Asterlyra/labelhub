"""单次审核执行的应用服务。"""

from __future__ import annotations

import logging
from time import perf_counter
from typing import Any

from annotation_agent.application.audit.audit_context_builder import AuditContextBuilder
from annotation_agent.application.ports import AuditResultRepository, LLMGateway
from annotation_agent.application.shared.flow_logging import elapsed_ms, summarize_payload
from annotation_agent.contracts.audit_request import AuditRequest
from annotation_agent.domain.audit.audit_result_factory import AuditResultFactory
from annotation_agent.domain.audit.decision_policy import AuditDecisionPolicy

logger = logging.getLogger(__name__)


# 审核执行服务：负责单次审核请求的完整执行流程（缓存检查 -> LLM推理 -> 决策判定 -> 结果持久化）
class AuditExecutionService:
    def __init__(
        self,
        audit_repository: AuditResultRepository,
        llm_gateway: LLMGateway,
        context_builder: AuditContextBuilder,
        decision_policy: AuditDecisionPolicy,
        result_factory: AuditResultFactory,
    ) -> None:
        self.audit_repository = audit_repository
        self.llm_gateway = llm_gateway
        self.context_builder = context_builder
        self.decision_policy = decision_policy
        self.result_factory = result_factory

    # 执行审核流程：构建请求上下文、检查缓存、调用 LLM、判定决策、保存结果
    def execute(self,
                task_id: str,
                payload: dict[str, Any] | None = None, force: bool = False) -> dict[str, Any]:

        started_at = perf_counter()
        payload = payload or {}

        logger.info(
            "【审核执行】开始: task_id=%s, task_item_id=%s, force=%s, 输入摘要=%s",
            task_id,
            payload.get("taskItemId", ""),
            force,
            summarize_payload(
                payload,
                keys=(
                    "taskItemId",
                    "taskTitle",
                    "rawData",
                    "annotationSchema",
                    "annotationResult",
                    "reviewConfig",
                ),
            ),
        )
        # 将原始载荷转换为审核请求对象
        request = AuditRequest.from_payload(task_id, payload)

        # 构建审核上下文（包含提示词、维度、验证结果等）
        context = self.context_builder.build(request)
        
        logger.info(
            "【审核执行】上下文完成: task_id=%s, task_item_id=%s, schema字段=%s个, 审核维度=%s个, "
            "规则问题=%s个, prompt长度=%s, input_hash=%s",
            request.task_id,
            request.task_item_id,
            len(context.schema_fields),
            len(context.dimensions),
            len(context.validation_issues),
            len(context.prompt),
            context.input_hash[:12],
        )

        # 缓存检查：若存在相同输入哈希的审核结果则直接复用
        result_key = request.task_item_id or task_id
        if not force:
            cached_result = self.audit_repository.get_result_by_input_hash(result_key, context.input_hash)
            if cached_result:
                logger.info(
                    "【审核执行】命中缓存: task_id=%s, task_item_id=%s, 状态=%s, 耗时=%sms",
                    task_id,
                    result_key,
                    cached_result.get("auditStatus", cached_result.get("status", "")),
                    elapsed_ms(started_at, perf_counter()),
                )
                return cached_result

        # 保存执行追踪信息，便于问题排查与审计
        self.audit_repository.save_execution_trace(
            task_item_id=result_key,
            task_id=request.task_id,
            llm_prompt=context.prompt,
            input_hash=context.input_hash,
        )

        # 调用 LLM 网关执行审核推理
        llm_started_at = perf_counter()
        logger.info(
            "【审核执行】调用LLM: task_id=%s, task_item_id=%s, 审核维度=%s个",
            task_id,
            result_key,
            len(context.dimensions),
        )
        model_result = self.llm_gateway.invoke_audit(
            context.prompt,
            context.dimensions,
            context.to_llm_context(),
            prompt_messages=context.prompt_messages,
        )
        logger.info(
            "【审核执行】LLM返回: task_id=%s, task_item_id=%s, 是否成功=%s, 耗时=%sms",
            task_id,
            result_key,
            model_result is not None,
            elapsed_ms(llm_started_at, perf_counter()),
        )
        # LLM 返回空结果时记录失败
        if model_result is None:
            saved_result = self.audit_repository.save_result(
                self.result_factory.build_llm_failure_result(context)
            )
            logger.info(
                "【审核执行】失败结果已保存: task_id=%s, task_item_id=%s, audit_status=%s, 总耗时=%sms",
                task_id,
                result_key,
                saved_result.get("auditStatus", ""),
                elapsed_ms(started_at, perf_counter()),
            )
            return saved_result

        # 根据 LLM 输出和配置执行决策策略，生成最终审核结论
        decision = self.decision_policy.decide(model_result,
                                               context.config,
                                               context.validation_issues)
        logger.info(
            "【审核执行】决策完成: task_id=%s, task_item_id=%s, audit_status=%s, 加权分=%s, "
            "模型评分=%s个, 问题=%s个",
            task_id,
            result_key,
            decision.get("decision", ""),
            decision.get("weightedScore", ""),
            len(model_result.get("scores", [])),
            len(model_result.get("issues", [])),
        )

        saved_result = self.audit_repository.save_result(
            self.result_factory.build_success_result(context,
                                                     model_result,
                                                     decision)
        )
        logger.info(
            "【审核执行】完成: task_id=%s, task_item_id=%s, audit_status=%s, 总耗时=%sms, 输出摘要=%s",
            task_id,
            result_key,
            saved_result.get("auditStatus", ""),
            elapsed_ms(started_at, perf_counter()),
            summarize_payload(
                saved_result,
                keys=("status", "auditStatus", "weightedScore", "confidence", "scores", "issues"),
            ),
        )
        return saved_result
