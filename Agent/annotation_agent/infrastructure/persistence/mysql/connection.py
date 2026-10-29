"""MySQL连接辅助工具。"""

from __future__ import annotations

import queue
import threading
import time
from dataclasses import dataclass
from typing import Any

from annotation_agent.app.settings import MySQLSettings


class MySQLUnavailableError(RuntimeError):
    """当PyMySQL依赖不可用时抛出。"""


@dataclass
class _PoolEntry:
    """池内连接条目，记录底层连接和创建时间。"""

    connection: Any
    created_at: float


class _PooledConnectionContext:
    """连接池借出的上下文包装器，退出时归还连接。"""

    def __init__(self, factory: "MySQLConnectionFactory", entry: _PoolEntry) -> None:
        self._factory = factory
        self._entry = entry
        self._released = False

    def __enter__(self) -> Any:
        return self._entry.connection

    def __exit__(self, exc_type, exc, tb) -> bool:
        self.close()
        return False

    def close(self) -> None:
        """归还或关闭底层连接，仅执行一次。"""
        if self._released:
            return
        self._released = True
        self._factory.release(self._entry)


class MySQLConnectionFactory:
    """MySQL连接工厂，提供带连接池的PyMySQL连接。"""

    def __init__(self, settings: MySQLSettings) -> None:
        """初始化连接工厂，保存MySQL配置并创建池。"""
        self.settings = settings
        self._idle_connections: queue.LifoQueue[_PoolEntry] = queue.LifoQueue(maxsize=settings.pool_size)
        self._lock = threading.Lock()
        self._total_connections = 0

    def connect(self) -> _PooledConnectionContext:
        """借出一个可用的MySQL连接。"""
        while True:
            entry = self._acquire_idle_connection()
            if entry is not None:
                return _PooledConnectionContext(self, entry)

            with self._lock:
                if self._total_connections < self.settings.pool_size:
                    self._total_connections += 1
                    should_create = True
                else:
                    should_create = False

            if should_create:
                try:
                    connection = self._create_connection()
                except Exception:
                    with self._lock:
                        self._total_connections -= 1
                    raise
                return _PooledConnectionContext(self, _PoolEntry(connection=connection, created_at=time.monotonic()))

            try:
                entry = self._idle_connections.get(timeout=self.settings.pool_timeout)
            except queue.Empty as exc:
                raise TimeoutError(
                    f"Timed out after {self.settings.pool_timeout}s waiting for a MySQL connection from the pool."
                ) from exc

            if self._is_connection_usable(entry):
                return _PooledConnectionContext(self, entry)
            self._discard_entry(entry)

    def release(self, entry: _PoolEntry) -> None:
        """归还连接到池中；不可复用时直接关闭。"""
        if self._is_stale(entry) or not getattr(entry.connection, "open", True):
            self._discard_entry(entry)
            return
        try:
            self._idle_connections.put_nowait(entry)
        except queue.Full:
            self._discard_entry(entry)

    def close(self) -> None:
        """关闭所有空闲连接，通常用于测试清理。"""
        while True:
            try:
                entry = self._idle_connections.get_nowait()
            except queue.Empty:
                return
            self._discard_entry(entry)

    def _acquire_idle_connection(self) -> _PoolEntry | None:
        while True:
            try:
                entry = self._idle_connections.get_nowait()
            except queue.Empty:
                return None
            if self._is_connection_usable(entry):
                return entry
            self._discard_entry(entry)

    def _is_connection_usable(self, entry: _PoolEntry) -> bool:
        if self._is_stale(entry):
            return False
        connection = entry.connection
        if not getattr(connection, "open", True):
            return False
        try:
            connection.ping(reconnect=True)
        except Exception:
            return False
        return True

    def _is_stale(self, entry: _PoolEntry) -> bool:
        recycle_after = self.settings.pool_recycle_seconds
        if recycle_after <= 0:
            return False
        return (time.monotonic() - entry.created_at) >= recycle_after

    def _discard_entry(self, entry: _PoolEntry) -> None:
        try:
            entry.connection.close()
        except Exception:
            pass
        finally:
            with self._lock:
                self._total_connections = max(0, self._total_connections - 1)

    def _create_connection(self) -> Any:
        try:
            import pymysql
        except ImportError as exc:  # pragma: no cover - optional runtime dependency
            raise MySQLUnavailableError("Missing PyMySQL dependency. Run pip install -r requirements.txt") from exc

        return pymysql.connect(
            host=self.settings.host,
            port=self.settings.port,
            user=self.settings.user,
            password=self.settings.password,
            database=self.settings.database,
            charset=self.settings.charset,
            connect_timeout=self.settings.connect_timeout,
            autocommit=True,
            cursorclass=pymysql.cursors.DictCursor,
        )


def dumps_json(data: Any) -> str:
    """将Python对象序列化为JSON字符串（支持中文等非ASCII字符）。"""
    import json

    return json.dumps(data, ensure_ascii=False)


def loads_json(raw: str | bytes | None, default: Any) -> Any:
    """将JSON字符串或字节反序列化为Python对象，空值时返回默认值。"""
    import json

    if raw in (None, ""):
        return default
    if isinstance(raw, bytes):
        raw = raw.decode("utf-8")
    return json.loads(raw)
