"""非破坏性MySQL迁移执行器。"""

from __future__ import annotations

import logging
from pathlib import Path

from annotation_agent.infrastructure.persistence.mysql.connection import MySQLConnectionFactory

logger = logging.getLogger(__name__)


class MySQLMigrationRunner:
    """非破坏性迁移执行器，按版本号顺序执行未应用的SQL迁移文件。"""

    def __init__(self,
                 connection_factory: MySQLConnectionFactory,
                 migrations_dir: Path) -> None:
        """初始化迁移执行器，指定连接工厂和迁移文件目录。"""
        self.connection_factory = connection_factory
        self.migrations_dir = migrations_dir

    def run(self) -> None:
        """执行所有未应用的迁移文件。"""
        migration_files = sorted(self.migrations_dir.glob("*.sql"))
        if not migration_files:
            return

        with self.connection_factory.connect() as connection:
            with connection.cursor() as cursor:
                # 确保迁移版本记录表存在
                cursor.execute(
                    """
                    CREATE TABLE IF NOT EXISTS agent_schema_migrations (
                      version VARCHAR(128) PRIMARY KEY,
                      applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """
                )
                # 查询已应用的迁移版本
                cursor.execute("SELECT version FROM agent_schema_migrations")
                applied_versions = {row["version"] for row in cursor.fetchall()}

                # 逐个执行未应用的迁移文件
                for migration_file in migration_files:
                    if migration_file.name in applied_versions:
                        continue
                    sql_text = migration_file.read_text(encoding="utf-8")
                    for statement in _split_sql_statements(sql_text):
                        try:
                            cursor.execute(statement)
                        except Exception as exc:
                            if _can_skip_schema_drift(statement, exc):
                                logger.warning(
                                    "跳过已存在的迁移变更, version=%s, statement=%s, error=%s",
                                    migration_file.name,
                                    statement,
                                    exc,
                                )
                                continue
                            raise
                    # 记录已应用的版本号
                    cursor.execute(
                        "INSERT INTO agent_schema_migrations (version) VALUES (%s)",
                        (migration_file.name,),
                    )


def _split_sql_statements(sql_text: str) -> list[str]:
    """按分号分割SQL文本为独立语句列表。"""
    statements = []
    for statement in sql_text.split(";"):
        cleaned_statement = statement.strip()
        if cleaned_statement:
            statements.append(cleaned_statement)
    return statements


def _can_skip_schema_drift(statement: str, exc: Exception) -> bool:
    """判断异常是否属于可安全跳过的非破坏性schema漂移。"""
    error_code = _extract_mysql_error_code(exc)
    normalized = " ".join(statement.lower().split())
    if error_code == 1060 and normalized.startswith("alter table") and " add column " in normalized:
        return True
    if error_code == 1061 and normalized.startswith("alter table") and (
        " add index " in normalized or " add key " in normalized or " add unique " in normalized
    ):
        return True
    if error_code == 1050 and normalized.startswith("create table"):
        return True
    return False


def _extract_mysql_error_code(exc: Exception) -> int | None:
    """尽量从数据库异常中提取MySQL错误码。"""
    if not getattr(exc, "args", None):
        return None
    first_arg = exc.args[0]
    return first_arg if isinstance(first_arg, int) else None
