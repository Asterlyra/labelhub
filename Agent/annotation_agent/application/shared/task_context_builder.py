"""审核与建议命令共享的任务上下文构建器。"""

from __future__ import annotations

from typing import Any

from annotation_agent.application.shared.idempotency_service import IdempotencyService
from annotation_agent.domain.shared.task_context import TaskContext
from annotation_agent.domain.shared.task_schema import AnnotationSchemaParser


# 任务上下文构建器：将请求转化为审核/建议共用的 TaskContext 值对象
class TaskContextBuilder:
    def __init__(
        self,
        schema_parser: AnnotationSchemaParser | None = None,
        idempotency_service: IdempotencyService | None = None,
    ) -> None:
        self.schema_parser = schema_parser or AnnotationSchemaParser()
        self.idempotency_service = idempotency_service or IdempotencyService()

    # 根据请求构建完整的任务上下文，包括解析标注 schema、计算输入哈希与幂等键
    def build(self, request: Any) -> TaskContext:
        # 解析标注 schema 字段定义
        schema_fields = self.schema_parser.parse(request.annotation_schema)
        # 计算请求载荷的哈希值，用于缓存命中与幂等判断
        input_hash = self.idempotency_service.build_payload_hash(self._hash_payload(request))
        return TaskContext(
            task_id=request.task_id,
            project_id=str(getattr(request, "project_id", "") or ""),
            task_item_id=str(getattr(request, "task_item_id", "") or ""),
            task_title=str(getattr(request, "task_title", "") or ""),
            task_description=str(getattr(request, "task_description", "") or ""),
            raw_data=request.raw_data,
            schema_fields=schema_fields,
            input_hash=input_hash,
            idempotency_key=self.idempotency_service.build_key(request.task_id, input_hash),
        )

    # 构建用于计算哈希的载荷字典；审核请求包含 annotation_result，建议请求不包含
    @staticmethod
    def _hash_payload(request: Any) -> dict[str, Any]:
        # 审核请求：包含标注结果与审核配置
        if hasattr(request, "annotation_result"):
            return {
                "projectId": getattr(request, "project_id", ""),
                "taskTitle": getattr(request, "task_title", ""),
                "taskDescription": getattr(request, "task_description", ""),
                "rawData": request.raw_data,
                "annotationSchema": request.annotation_schema,
                "annotationResult": request.annotation_result,
                "reviewConfig": request.review_config,
            }

        # 建议请求：仅包含基础字段
        payload = {
            "projectId": getattr(request, "project_id", ""),
            "taskTitle": getattr(request, "task_title", ""),
            "taskDescription": getattr(request, "task_description", ""),
            "rawData": request.raw_data,
            "annotationSchema": request.annotation_schema,
        }
        # 可选字段：建议选项、目标字段等
        for optional_field in ("suggestion_options", "target_fields"):
            if hasattr(request, optional_field):
                payload[optional_field] = getattr(request, optional_field)
        return payload
