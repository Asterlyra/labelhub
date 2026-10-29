"""测试MySQL审核仓储与迁移执行器。"""

import shutil
from pathlib import Path
from uuid import uuid4

from annotation_agent.infrastructure.persistence.mysql.audit_result_repository import MySQLAuditResultRepository
from annotation_agent.infrastructure.persistence.mysql.migration_runner import MySQLMigrationRunner
from annotation_agent.infrastructure.persistence.mysql.suggestion_job_repository import MySQLSuggestionJobRepository


# 模拟数据库游标
class FakeCursor:
    def __init__(self, fetchall_rows=None, fetchone_row=None, error_map=None):
        self.fetchall_rows = list(fetchall_rows or [])
        self.fetchone_row = fetchone_row
        self.executed = []
        self.error_map = error_map or {}

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, tb):
        return False

    def execute(self, sql, params=None):
        self.executed.append((sql, params))
        for pattern, error in self.error_map.items():
            if pattern in sql:
                raise error

    def fetchall(self):
        return list(self.fetchall_rows)

    def fetchone(self):
        return self.fetchone_row


# 模拟数据库连接
class FakeConnection:
    def __init__(self, cursor):
        self._cursor = cursor

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, tb):
        return False

    def cursor(self):
        return self._cursor


# 模拟数据库连接工厂
class FakeConnectionFactory:
    def __init__(self, cursor):
        self.cursor_obj = cursor

    def connect(self):
        return FakeConnection(self.cursor_obj)


# 验证迁移执行器应用新SQL文件且不执行破坏性重置
def test_migration_runner_applies_new_sql_files_without_destructive_reset():
    migrations_dir = Path(__file__).resolve().parent / f"_tmp_migrations_{uuid4().hex[:8]}"
    migrations_dir.mkdir()
    try:
        (migrations_dir / "001_create_table.sql").write_text(
            "CREATE TABLE demo_table (id INT PRIMARY KEY);",
            encoding="utf-8",
        )
        (migrations_dir / "002_add_column.sql").write_text(
            "ALTER TABLE demo_table ADD COLUMN name VARCHAR(32);",
            encoding="utf-8",
        )

        cursor = FakeCursor(fetchall_rows=[])
        runner = MySQLMigrationRunner(FakeConnectionFactory(cursor), migrations_dir)

        runner.run()
    finally:
        shutil.rmtree(migrations_dir, ignore_errors=True)

    sql_statements = [sql for sql, _ in cursor.executed]
    assert any("CREATE TABLE IF NOT EXISTS agent_schema_migrations" in sql for sql in sql_statements)
    assert any("CREATE TABLE demo_table" in sql for sql in sql_statements)
    assert any("ALTER TABLE demo_table ADD COLUMN name" in sql for sql in sql_statements)
    assert any("INSERT INTO agent_schema_migrations" in sql for sql in sql_statements)
    assert not any("DROP TABLE" in sql.upper() for sql in sql_statements)


def test_migration_runner_tolerates_duplicate_column_schema_drift():
    migrations_dir = Path(__file__).resolve().parent / f"_tmp_migrations_{uuid4().hex[:8]}"
    migrations_dir.mkdir()
    try:
        (migrations_dir / "003_add_column.sql").write_text(
            "ALTER TABLE demo_table ADD COLUMN media_result_json JSON NULL;",
            encoding="utf-8",
        )

        cursor = FakeCursor(
            fetchall_rows=[],
            error_map={
                "ALTER TABLE demo_table ADD COLUMN media_result_json JSON NULL": RuntimeError(
                    1060,
                    "Duplicate column name 'media_result_json'",
                )
            },
        )
        runner = MySQLMigrationRunner(FakeConnectionFactory(cursor), migrations_dir)

        runner.run()
    finally:
        shutil.rmtree(migrations_dir, ignore_errors=True)

    sql_statements = [sql for sql, _ in cursor.executed]
    assert any("ALTER TABLE demo_table ADD COLUMN media_result_json JSON NULL" in sql for sql in sql_statements)
    assert any("INSERT INTO agent_schema_migrations" in sql for sql in sql_statements)


# 验证仓储正确保存执行追踪载荷和提示词
def test_repository_saves_execution_trace_payload_and_prompt():
    cursor = FakeCursor()
    repository = MySQLAuditResultRepository(FakeConnectionFactory(cursor))

    repository.save_execution_trace(
        "task-item-1",
        "task-1",
        inbound_event={"taskItemId": "task-item-1", "rawData": {"foo": "bar"}},
        llm_prompt="system\n\nuser",
        input_hash="hash-1",
    )

    sql, params = cursor.executed[-1]
    assert "mq_payload_json" in sql
    assert "llm_prompt_text" in sql
    assert params[0] == "task-item-1"
    assert params[1] == "task-1"
    assert params[2] == "hash-1"
    assert params[5] == '{"taskItemId": "task-item-1", "rawData": {"foo": "bar"}}'
    assert params[6] == "system\n\nuser"


def test_repository_lists_results_by_task_id_from_agent_table():
    cursor = FakeCursor(
        fetchall_rows=[
            {
                "task_item_id": 101,
                "task_id": "task-1",
                "status": "completed",
                "audit_status": "pass",
            },
            {
                "task_item_id": 102,
                "task_id": "task-1",
                "status": "",
                "audit_status": "",
            },
        ]
    )
    repository = MySQLAuditResultRepository(FakeConnectionFactory(cursor))

    result = repository.list_results_by_task_id("task-1")

    sql, params = cursor.executed[-1]
    assert "FROM agent_audit_results" in sql
    assert "WHERE task_id = %s" in sql
    assert params == ("task-1",)
    assert result == [
        {
            "taskItemId": "101",
            "taskId": "task-1",
            "status": "completed",
            "auditStatus": "pass",
        },
        {
            "taskItemId": "102",
            "taskId": "task-1",
            "status": "",
            "auditStatus": "",
        },
    ]


def test_audit_repository_does_not_treat_pending_trace_as_hash_cache():
    cursor = FakeCursor(
        fetchone_row={
            "task_item_id": "item-1",
            "task_id": "task-1",
            "input_hash": "hash-1",
            "status": "pending",
            "audit_status": "",
            "mq_payload_json": "{}",
            "llm_prompt_text": "",
            "scores_json": None,
            "summary": "",
            "confidence": 0,
            "media_result_json": None,
            "weighted_score": 0,
            "attempt_count": 1,
            "last_error": "",
            "created_at": "",
        }
    )
    repository = MySQLAuditResultRepository(FakeConnectionFactory(cursor))

    result = repository.get_result_by_input_hash("item-1", "hash-1")

    assert result is None


def test_suggestion_repository_queries_by_suggestion_id_and_task_item_id():
    cursor = FakeCursor(
        fetchone_row={
            "suggestion_id": "sug-1",
            "task_id": "task-1",
            "task_item_id": "item-1",
            "status": "completed",
            "input_hash": "hash-1",
            "request_payload_json": "{}",
            "annotation_data_json": '{"label": "ok"}',
            "llm_prompt_text": "prompt",
            "raw_model_output_text": "{}",
            "attempt_count": 1,
            "last_error": "",
            "created_at": "",
            "updated_at": "",
        }
    )
    repository = MySQLSuggestionJobRepository(FakeConnectionFactory(cursor))

    result = repository.get_job_for_task_item("sug-1", "item-1")

    sql, params = cursor.executed[-1]
    assert "WHERE suggestion_id = %s" in sql
    assert "AND task_item_id = %s" in sql
    assert params == ("sug-1", "item-1")
    assert result["suggestionId"] == "sug-1"
    assert result["taskItemId"] == "item-1"
    assert result["annotationData"] == {"label": "ok"}


def test_suggestion_repository_queries_completed_job_by_task_item_hash():
    cursor = FakeCursor(
        fetchone_row={
            "suggestion_id": "sug-cached",
            "task_id": "task-1",
            "task_item_id": "item-1",
            "status": "completed",
            "input_hash": "hash-1",
            "request_payload_json": "{}",
            "annotation_data_json": '{"label": "cached"}',
            "llm_prompt_text": "prompt",
            "raw_model_output_text": "{}",
            "attempt_count": 1,
            "last_error": "",
            "created_at": "",
            "updated_at": "",
        }
    )
    repository = MySQLSuggestionJobRepository(FakeConnectionFactory(cursor))

    result = repository.get_completed_job_by_task_item_hash("item-1", "hash-1")

    sql, params = cursor.executed[-1]
    assert "WHERE task_item_id = %s" in sql
    assert "AND input_hash = %s" in sql
    assert "AND status = %s" in sql
    assert params == ("item-1", "hash-1", "completed")
    assert result["suggestionId"] == "sug-cached"
    assert result["annotationData"] == {"label": "cached"}


def test_suggestion_repository_queries_completed_job_by_input_hash():
    cursor = FakeCursor(
        fetchone_row={
            "suggestion_id": "sug-global",
            "task_id": "task-1",
            "task_item_id": "item-1",
            "status": "completed",
            "input_hash": "hash-1",
            "request_payload_json": "{}",
            "annotation_data_json": '{"label": "global"}',
            "llm_prompt_text": "prompt",
            "raw_model_output_text": "{}",
            "attempt_count": 1,
            "last_error": "",
            "created_at": "",
            "updated_at": "",
        }
    )
    repository = MySQLSuggestionJobRepository(FakeConnectionFactory(cursor))

    result = repository.get_completed_job_by_input_hash("hash-1")

    sql, params = cursor.executed[-1]
    assert "WHERE input_hash = %s" in sql
    assert "AND status = %s" in sql
    assert params == ("hash-1", "completed")
    assert result["suggestionId"] == "sug-global"
    assert result["annotationData"] == {"label": "global"}
