"""审核任务生命周期管理的应用服务。"""

from __future__ import annotations

import logging
from time import perf_counter
from typing import Any

from annotation_agent.application.audit.audit_execution_service import AuditExecutionService
from annotation_agent.application.ports import AuditResultRepository
from annotation_agent.application.shared.flow_logging import elapsed_ms, summarize_payload
from annotation_agent.application.shared.task_context_builder import TaskContextBuilder
from annotation_agent.app.settings import RabbitMQSettings
from annotation_agent.contracts.audit_request import AuditRequest
from annotation_agent.domain.shared.enums import TaskStatus
from annotation_agent.infrastructure.integrations.backend_contract_mapper import backend_context_to_audit_payload

logger = logging.getLogger(__name__)


# 重试次数耗尽异常：当审核任务已用尽最大重试次数时抛出
class MaxRetriesExceededError(Exception):
    """当审核任务已用尽最大重试次数时抛出。"""


# 审核任务服务：管理审核任务从接收到完成/失败的完整生命周期
class AuditJobService:
    def __init__(
        self,
        audit_execution_service: AuditExecutionService,
        audit_repository: AuditResultRepository,
        rabbitmq_settings: RabbitMQSettings,
        task_context_builder: TaskContextBuilder | None = None,
    ) -> None:
        self.audit_execution_service = audit_execution_service
        self.audit_repository = audit_repository
        self.rabbitmq_settings = rabbitmq_settings
        self.task_context_builder = task_context_builder or TaskContextBuilder()

    # 处理来自后端的审核事件：校验参数、检查重试次数、执行审核、处理成功/失败
    def process_backend_event(self,
                              event: dict[str, Any]) -> dict[str, Any]:
        started_at = perf_counter()
        # 从事件中提取并校验必需参数
        task_item_id = str(event.get("taskItemId") or "").strip()
        if not task_item_id:
            raise ValueError("RabbitMQ audit message missing taskItemId")

        task_id = str(event.get("taskId") or "").strip()
        if not task_id:
            raise ValueError("RabbitMQ audit message missing taskId")

        logger.info(
            "【审核任务】收到MQ消息: task_id=%s, task_item_id=%s, 输入摘要=%s",
            task_id,
            task_item_id,

            summarize_payload(
                event,
                keys=(
                    "taskId",
                    "taskItemId",
                    "taskTitle",
                    "materials",
                    "rawData",
                    "annotationData",
                    "annotationResult",
                    "reviewConfig",
                ),
            ),
        )

        # 将后端事件转换为审核载荷，并计算当前MQ内容哈希用于重复消息判定
        payload = backend_context_to_audit_payload(event)
        current_input_hash = self._build_input_hash(task_id, payload)

        # 重试次数检查：超过最大重试次数则标记为耗尽并抛出异常
        existing_record = self.audit_repository.get_job_record(task_item_id)
        duplicate_result = self._try_short_circuit_duplicate(
            task_id,
            task_item_id,
            current_input_hash,
            existing_record,
            started_at,
        )
        
        if duplicate_result is not None:
            return duplicate_result

        existing_input_hash = str((existing_record or {}).get("inputHash") or "").strip()
        current_attempt = (
            (existing_record or {}).get("attemptCount", 0)
            if existing_input_hash == current_input_hash
            else 0
        )
        logger.info(
            "【审核任务】重试检查: task_id=%s, task_item_id=%s, 当前次数=%s, 最大次数=%s",
            task_id,
            task_item_id,
            current_attempt,
            self.rabbitmq_settings.max_retries,
        )

        if current_attempt >= self.rabbitmq_settings.max_retries:
            error_message = (
                f"Retry attempts exhausted ({current_attempt}/{self.rabbitmq_settings.max_retries}), "
                "routing to manual review"
            )
            self.audit_repository.mark_max_retries_exceeded(task_item_id,
                                                            error_message,
                                                            task_id=task_id)
            logger.info(
                "【审核任务】重试耗尽: task_id=%s, task_item_id=%s, 状态=max_retries_exceeded, 耗时=%sms",
                task_id,
                task_item_id,
                elapsed_ms(started_at, perf_counter()),
            )
            raise MaxRetriesExceededError(error_message)

        # 标记任务为待处理并保存入站事件追踪
        self.audit_repository.mark_pending(task_item_id,
                                           task_id=task_id,
                                           increment_attempt=True)
        logger.info(
            "【审核任务】状态更新: task_id=%s, task_item_id=%s, 状态=pending, 本次尝试=%s",
            task_id,
            task_item_id,
            current_attempt + 1,
        )

        self.audit_repository.save_execution_trace(task_item_id,
                                                   task_id,
                                                   inbound_event=event,
                                                   input_hash=current_input_hash)

        try:
            logger.info(
                "【审核任务】后端事件已转换: task_id=%s, task_item_id=%s, payload摘要=%s",
                task_id,
                task_item_id,
                summarize_payload(
                    payload,
                    keys=("taskId", "taskItemId", "rawData", "annotationSchema", "annotationResult", "reviewConfig"),
                ),
            )
            self.audit_repository.mark_processing(task_item_id, task_id=task_id)
            logger.info(
                "【审核任务】状态更新: task_id=%s, task_item_id=%s, 状态=processing",
                task_id,
                task_item_id,
            )
            # 执行核心审核逻辑
            result = self.audit_execution_service.execute(task_id, payload)
            self.audit_repository.mark_completed(task_item_id, result)
            logger.info(
                "【审核任务】完成: task_id=%s, task_item_id=%s, audit_status=%s, 总耗时=%sms",
                task_id,
                task_item_id,
                result.get("auditStatus", result.get("status", "")),
                elapsed_ms(started_at, perf_counter()),
            )
            return result
        except MaxRetriesExceededError:
            raise
        except Exception as exc:
            # 审核执行失败：标记失败状态并记录异常日志
            self.audit_repository.mark_failed(task_item_id,
                                              str(exc),
                                              task_id=task_id)

            logger.info(
                "【审核任务】失败: task_id=%s, task_item_id=%s, 错误=%s, 总耗时=%sms",
                task_id,
                task_item_id,
                exc,
                elapsed_ms(started_at, perf_counter()),
            )
            logger.exception("审核任务执行失败, task_item_id=%s", task_item_id)
            raise

    def _build_input_hash(self, task_id: str, payload: dict[str, Any]) -> str:
        request = AuditRequest.from_payload(task_id, payload)
        return self.task_context_builder.build(request).input_hash

    def _try_short_circuit_duplicate(
        self,
        task_id: str,
        task_item_id: str,
        current_input_hash: str,
        existing_record: dict[str, Any] | None,
        started_at: float,
    ) -> dict[str, Any] | None:
        if not existing_record:
            return None

        status = str(existing_record.get("status") or "").strip()
        duplicate_statuses = {
            TaskStatus.PENDING.value,
            TaskStatus.PROCESSING.value,
            TaskStatus.COMPLETED.value,
            TaskStatus.MAX_RETRIES_EXCEEDED.value,
        }
        if status not in duplicate_statuses:
            return None

        existing_input_hash = str(existing_record.get("inputHash") or "").strip()
        if existing_input_hash != current_input_hash:
            logger.info(
                "【审核任务】检测到同task_item_id的新内容，继续重新审核: task_id=%s, task_item_id=%s, old_hash=%s, new_hash=%s",
                task_id,
                task_item_id,
                existing_input_hash[:12],
                current_input_hash[:12],
            )
            return None

        logger.info(
            "【审核任务】检测到重复消息，直接跳过: task_id=%s, task_item_id=%s, 当前状态=%s, 耗时=%sms",
            task_id,
            task_item_id,
            status,
            elapsed_ms(started_at, perf_counter()),
        )
        if status == TaskStatus.COMPLETED.value and existing_record.get("result"):
            return existing_record["result"]
        return existing_record
