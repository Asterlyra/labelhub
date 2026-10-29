"""MySQL建议任务仓储。"""

from __future__ import annotations

from typing import Any

from annotation_agent.domain.shared.enums import TaskStatus
from annotation_agent.infrastructure.persistence.mysql.connection import MySQLConnectionFactory, dumps_json, loads_json


class MySQLSuggestionJobRepository:
    """基于MySQL的建议任务仓储，负责建议任务的创建和状态管理。"""

    def __init__(self, connection_factory: MySQLConnectionFactory) -> None:
        """初始化仓储，注入MySQL连接工厂。"""
        self.connection_factory = connection_factory

    def create_job(
        self,
        suggestion_id: str,
        task_id: str,
        task_item_id: str,
        input_hash: str,
        request_payload: dict[str, Any],
    ) -> dict[str, Any]:
        """创建新的建议任务记录，初始状态为待处理。"""
        with self.connection_factory.connect() as connection:
            with connection.cursor() as cursor:
                cursor.execute(
                    """
                    INSERT INTO agent_annotation_suggestions (
                      suggestion_id, task_id, task_item_id, status, input_hash,
                      request_payload_json, annotation_data_json, attempt_count, last_error
                    )
                    VALUES (%s, %s, %s, %s, %s, %s, NULL, 0, '')
                    """,
                    (
                        suggestion_id,
                        task_id,
                        task_item_id,
                        TaskStatus.PENDING,
                        input_hash,
                        dumps_json(request_payload),
                    ),
                )
        return self.get_job(suggestion_id) or {}

    def get_job(self,
                suggestion_id: str) -> dict[str, Any] | None:
        """根据建议ID查询任务记录，不存在时返回None。"""
        with self.connection_factory.connect() as connection:
            with connection.cursor() as cursor:
                cursor.execute(
                    """
                    SELECT suggestion_id, task_id, task_item_id, status, input_hash,
                           request_payload_json, annotation_data_json, llm_prompt_text,
                           raw_model_output_text, attempt_count, last_error, created_at, updated_at
                    FROM agent_annotation_suggestions
                    WHERE suggestion_id = %s
                    LIMIT 1
                    """,
                    (suggestion_id,),
                )
                row = cursor.fetchone()
        if not row:
            return None
        return self._row_to_job(row)

    def get_job_for_task_item(
        self,
        suggestion_id: str,
        task_item_id: str,
    ) -> dict[str, Any] | None:
        """根据建议ID和任务项ID查询任务记录，不存在时返回None。"""
        with self.connection_factory.connect() as connection:
            with connection.cursor() as cursor:
                cursor.execute(
                    """
                    SELECT suggestion_id, task_id, task_item_id, status, input_hash,
                           request_payload_json, annotation_data_json, llm_prompt_text,
                           raw_model_output_text, attempt_count, last_error, created_at, updated_at
                    FROM agent_annotation_suggestions
                    WHERE suggestion_id = %s
                      AND task_item_id = %s
                    LIMIT 1
                    """,
                    (suggestion_id, task_item_id),
                )
                row = cursor.fetchone()
        if not row:
            return None
        return self._row_to_job(row)

    def get_completed_job_by_task_item_hash(
        self,
        task_item_id: str,
        input_hash: str,
    ) -> dict[str, Any] | None:
        """根据任务项ID和输入哈希查询已完成的历史建议任务。"""
        with self.connection_factory.connect() as connection:
            with connection.cursor() as cursor:
                cursor.execute(
                    """
                    SELECT suggestion_id, task_id, task_item_id, status, input_hash,
                           request_payload_json, annotation_data_json, llm_prompt_text,
                           raw_model_output_text, attempt_count, last_error, created_at, updated_at
                    FROM agent_annotation_suggestions
                    WHERE task_item_id = %s
                      AND input_hash = %s
                      AND status = %s
                    ORDER BY updated_at DESC, created_at DESC
                    LIMIT 1
                    """,
                    (task_item_id, input_hash, TaskStatus.COMPLETED),
                )
                row = cursor.fetchone()
        if not row:
            return None
        return self._row_to_job(row)

    def get_completed_job_by_input_hash(
        self,
        input_hash: str,
    ) -> dict[str, Any] | None:
        """根据输入哈希查询已完成的历史建议任务。"""
        with self.connection_factory.connect() as connection:
            with connection.cursor() as cursor:
                cursor.execute(
                    """
                    SELECT suggestion_id, task_id, task_item_id, status, input_hash,
                           request_payload_json, annotation_data_json, llm_prompt_text,
                           raw_model_output_text, attempt_count, last_error, created_at, updated_at
                    FROM agent_annotation_suggestions
                    WHERE input_hash = %s
                      AND status = %s
                    ORDER BY updated_at DESC, created_at DESC
                    LIMIT 1
                    """,
                    (input_hash, TaskStatus.COMPLETED),
                )
                row = cursor.fetchone()
        if not row:
            return None
        return self._row_to_job(row)

    def mark_processing(self,
                        suggestion_id: str,
                        llm_prompt: str) -> dict[str, Any] | None:
        """将任务标记为处理中，记录LLM提示词并增加尝试次数。"""
        with self.connection_factory.connect() as connection:
            with connection.cursor() as cursor:
                cursor.execute(
                    """
                    UPDATE agent_annotation_suggestions
                    SET status = %s,
                        llm_prompt_text = %s,
                        attempt_count = attempt_count + 1,
                        last_error = ''
                    WHERE suggestion_id = %s
                    """,
                    (TaskStatus.PROCESSING, llm_prompt, suggestion_id),
                )
        return self.get_job(suggestion_id)

    def mark_completed(
        self,
        suggestion_id: str,
        annotation_data: dict[str, Any],
        raw_model_output: str,
        llm_prompt: str | None = None,
    ) -> dict[str, Any] | None:
        """将任务标记为已完成，写入建议标注数据和原始模型输出。"""
        with self.connection_factory.connect() as connection:
            with connection.cursor() as cursor:
                cursor.execute(
                    """
                    UPDATE agent_annotation_suggestions
                    SET status = %s,
                        annotation_data_json = %s,
                        raw_model_output_text = %s,
                        llm_prompt_text = %s,
                        last_error = ''
                    WHERE suggestion_id = %s
                    """,
                    (
                        TaskStatus.COMPLETED,
                        dumps_json(annotation_data),
                        raw_model_output,
                        llm_prompt or "",
                        suggestion_id,
                    ),
                )
        return self.get_job(suggestion_id)

    def mark_failed(
        self,
        suggestion_id: str,
        error: str,
        raw_model_output: str = "",
        llm_prompt: str | None = None,
    ) -> dict[str, Any] | None:
        """将任务标记为失败，记录错误信息和原始模型输出。"""
        with self.connection_factory.connect() as connection:
            with connection.cursor() as cursor:
                cursor.execute(
                    """
                    UPDATE agent_annotation_suggestions
                    SET status = %s,
                        raw_model_output_text = %s,
                        llm_prompt_text = %s,
                        last_error = %s
                    WHERE suggestion_id = %s
                    """,
                    (
                        TaskStatus.FAILED,
                        raw_model_output,
                        llm_prompt or "",
                        error,
                        suggestion_id,
                    ),
                )
        return self.get_job(suggestion_id)

    @staticmethod
    def _row_to_job(row: dict[str, Any]) -> dict[str, Any]:
        """将数据库行转换为建议任务字典。"""
        return {
            "suggestionId": row["suggestion_id"],
            "taskId": row["task_id"],
            "taskItemId": row["task_item_id"],
            "status": row["status"],
            "inputHash": row["input_hash"] or "",
            "requestPayload": loads_json(row.get("request_payload_json"), {}),
            "annotationData": loads_json(row.get("annotation_data_json"), None),
            "llmPrompt": row.get("llm_prompt_text") or "",
            "rawModelOutput": row.get("raw_model_output_text") or "",
            "attemptCount": int(row.get("attempt_count") or 0),
            "lastError": row.get("last_error") or "",
            "createdAt": str(row.get("created_at") or ""),
            "updatedAt": str(row.get("updated_at") or ""),
        }
