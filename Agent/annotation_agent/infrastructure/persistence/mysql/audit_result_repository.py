"""MySQL audit result repository."""

from __future__ import annotations

from typing import Any

from annotation_agent.domain.audit.audit_result import AuditResult
from annotation_agent.domain.shared.enums import TaskStatus
from annotation_agent.infrastructure.persistence.mysql.connection import (
    MySQLConnectionFactory,
    dumps_json,
    loads_json,
)


class MySQLAuditResultRepository:
    """Persist and query audit results in MySQL."""

    def __init__(self, connection_factory: MySQLConnectionFactory) -> None:
        self.connection_factory = connection_factory

    def save_result(self, result: AuditResult) -> dict[str, Any]:
        task_item_id = result.task_item_id or result.task_id
        with self.connection_factory.connect() as connection:
            with connection.cursor() as cursor:
                cursor.execute(
                    """
                    INSERT INTO agent_audit_results (
                      task_item_id, task_id, input_hash,
                      status, audit_status, scores_json, summary, confidence,
                      media_result_json,
                      weighted_score, last_error
                    )
                    VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
                    ON DUPLICATE KEY UPDATE
                      task_id = VALUES(task_id),
                      input_hash = VALUES(input_hash),
                      status = VALUES(status),
                      audit_status = VALUES(audit_status),
                      scores_json = VALUES(scores_json),
                      summary = VALUES(summary),
                      confidence = VALUES(confidence),
                      media_result_json = VALUES(media_result_json),
                      weighted_score = VALUES(weighted_score),
                      last_error = VALUES(last_error)
                    """,
                    (
                        task_item_id,
                        result.task_id,
                        result.input_hash,
                        result.status,
                        result.audit_status,
                        dumps_json(result.scores),
                        result.summary,
                        result.confidence,
                        dumps_json(result.media_result),
                        result.weighted_score,
                        result.last_error,
                    ),
                )
        return result.to_dict()

    def save_execution_trace(
        self,
        task_item_id: str,
        task_id: str,
        inbound_event: dict[str, Any] | None = None,
        llm_prompt: str | None = None,
        input_hash: str | None = None,
    ) -> None:
        with self.connection_factory.connect() as connection:
            with connection.cursor() as cursor:
                cursor.execute(
                    """
                    INSERT INTO agent_audit_results (
                      task_item_id, task_id, input_hash, status, summary,
                      mq_payload_json, llm_prompt_text
                    )
                    VALUES (%s, %s, %s, %s, %s, %s, %s)
                    ON DUPLICATE KEY UPDATE
                      task_id = VALUES(task_id),
                      input_hash = CASE
                        WHEN VALUES(input_hash) <> '' THEN VALUES(input_hash)
                        ELSE input_hash
                      END,
                      mq_payload_json = COALESCE(VALUES(mq_payload_json), mq_payload_json),
                      llm_prompt_text = COALESCE(VALUES(llm_prompt_text), llm_prompt_text)
                    """,
                    (
                        task_item_id,
                        task_id,
                        input_hash or "",
                        TaskStatus.PENDING,
                        "",
                        dumps_json(inbound_event) if inbound_event is not None else None,
                        llm_prompt,
                    ),
                )

    def get_result(self, task_item_id: str) -> dict[str, Any] | None:
        with self.connection_factory.connect() as connection:
            with connection.cursor() as cursor:
                cursor.execute(
                    """
                    SELECT task_item_id, task_id, input_hash,
                           status, audit_status, mq_payload_json, llm_prompt_text,
                           scores_json, summary, confidence, media_result_json,
                           weighted_score, attempt_count, last_error,
                           created_at
                    FROM agent_audit_results
                    WHERE task_item_id = %s
                    LIMIT 1
                    """,
                    (task_item_id,),
                )
                row = cursor.fetchone()
        if not row:
            return None
        return self._row_to_result_dict(row)

    def list_results_by_task_id(self, task_id: str) -> list[dict[str, Any]]:
        with self.connection_factory.connect() as connection:
            with connection.cursor() as cursor:
                cursor.execute(
                    """
                    SELECT task_item_id, task_id, status, audit_status
                    FROM agent_audit_results
                    WHERE task_id = %s
                    ORDER BY id ASC
                    """,
                    (task_id,),
                )
                rows = cursor.fetchall()
        return [
            {
                "taskItemId": str(row.get("task_item_id") or ""),
                "taskId": str(row.get("task_id") or ""),
                "status": row.get("status") or "",
                "auditStatus": row.get("audit_status") or "",
            }
            for row in rows
        ]

    def get_result_by_input_hash(
        self,
        task_item_id: str,
        input_hash: str,
    ) -> dict[str, Any] | None:
        result = self.get_result(task_item_id)
        if (
            result
            and result.get("inputHash") == input_hash
            and result.get("status") == TaskStatus.COMPLETED
            and result.get("scores")
        ):
            return result
        return None

    def get_job_record(self, task_item_id: str) -> dict[str, Any] | None:
        with self.connection_factory.connect() as connection:
            with connection.cursor() as cursor:
                cursor.execute("SELECT * FROM agent_audit_results WHERE task_item_id = %s", (task_item_id,))
                row = cursor.fetchone()
        return self._row_to_job_record(row) if row else None

    def mark_pending(
        self,
        task_item_id: str,
        task_id: str | None = None,
        increment_attempt: bool = False,
    ) -> dict[str, Any]:
        return self._upsert_status(
            task_item_id=task_item_id,
            status=TaskStatus.PENDING,
            task_id=task_id,
            increment_attempt=increment_attempt,
        )

    def mark_processing(
        self,
        task_item_id: str,
        task_id: str | None = None,
    ) -> dict[str, Any]:
        return self._upsert_status(
            task_item_id=task_item_id,
            status=TaskStatus.PROCESSING,
            task_id=task_id,
        )

    def mark_completed(self, task_item_id: str, result: dict[str, Any]) -> dict[str, Any]:
        with self.connection_factory.connect() as connection:
            with connection.cursor() as cursor:
                cursor.execute(
                    """
                    UPDATE agent_audit_results
                    SET status = %s,
                        audit_status = %s,
                        scores_json = %s,
                        summary = %s,
                        confidence = %s,
                        media_result_json = %s,
                        weighted_score = %s,
                        last_error = ''
                    WHERE task_item_id = %s
                    """,
                    (
                        TaskStatus.COMPLETED,
                        result.get("auditStatus", result.get("status", "")),
                        dumps_json(result.get("scores", [])),
                        result.get("summary", ""),
                        result.get("confidence", 0),
                        dumps_json(result.get("mediaResult")),
                        result.get("weightedScore", 0),
                        task_item_id,
                    ),
                )
        return self.get_job_record(task_item_id) or {"taskItemId": task_item_id, "status": TaskStatus.COMPLETED}

    def mark_failed(
        self,
        task_item_id: str,
        error: str,
        task_id: str | None = None,
    ) -> dict[str, Any]:
        return self._upsert_status(
            task_item_id,
            TaskStatus.FAILED,
            task_id=task_id,
            last_error=error,
        )

    def mark_max_retries_exceeded(
        self,
        task_item_id: str,
        error: str,
        task_id: str | None = None,
    ) -> dict[str, Any]:
        return self._upsert_status(
            task_item_id,
            TaskStatus.MAX_RETRIES_EXCEEDED,
            task_id=task_id,
            last_error=error,
        )

    def _upsert_status(
        self,
        task_item_id: str,
        status: TaskStatus,
        task_id: str | None = None,
        last_error: str | None = None,
        increment_attempt: bool = False,
    ) -> dict[str, Any]:
        with self.connection_factory.connect() as connection:
            with connection.cursor() as cursor:
                cursor.execute(
                    """
                    INSERT INTO agent_audit_results (
                      task_item_id, task_id, status, summary, attempt_count, last_error
                    )
                    VALUES (%s, %s, %s, %s, %s, %s)
                    ON DUPLICATE KEY UPDATE
                      status = VALUES(status),
                      attempt_count = attempt_count + %s,
                      last_error = COALESCE(VALUES(last_error), last_error)
                    """,
                    (
                        task_item_id,
                        task_id or task_item_id,
                        status,
                        "",
                        1 if increment_attempt else 0,
                        last_error,
                        1 if increment_attempt else 0,
                    ),
                )
        return self.get_job_record(task_item_id) or {"taskItemId": task_item_id, "status": status}

    @staticmethod
    def _row_to_result_dict(row: dict[str, Any]) -> dict[str, Any]:
        return {
            "taskId": row["task_id"],
            "taskItemId": row["task_item_id"],
            "inputHash": row["input_hash"],
            "status": row["status"],
            "auditStatus": row["audit_status"],
            "scores": loads_json(row["scores_json"], []),
            "summary": row["summary"] or "",
            "confidence": float(row["confidence"] or 0),
            "mediaResult": loads_json(row.get("media_result_json"), None),
            "weightedScore": float(row["weighted_score"] or 0),
            "attemptCount": int(row.get("attempt_count") or 0),
            "lastError": row.get("last_error") or "",
            "createdAt": str(row["created_at"]) if row["created_at"] else "",
        }

    @staticmethod
    def _row_to_job_record(row: dict[str, Any]) -> dict[str, Any]:
        return {
            "taskItemId": row["task_item_id"],
            "taskId": row["task_id"],
            "inputHash": row.get("input_hash") or "",
            "status": row["status"],
            "auditStatus": row["audit_status"],
            "attemptCount": int(row.get("attempt_count") or 0),
            "result": MySQLAuditResultRepository._row_to_result_dict(row) if row.get("scores_json") else None,
            "mqPayload": loads_json(row.get("mq_payload_json"), None),
            "llmPrompt": row.get("llm_prompt_text") or "",
            "lastError": row.get("last_error") or "",
        }
