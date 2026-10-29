"""并发运行时相关测试。"""

from __future__ import annotations

import sys
from types import SimpleNamespace

from annotation_agent.app.settings import MySQLSettings, RabbitMQSettings
from annotation_agent.application.audit.audit_job_service import MaxRetriesExceededError
from annotation_agent.infrastructure.persistence.mysql.connection import MySQLConnectionFactory
from annotation_agent.interfaces.messaging.rabbitmq_audit_consumer import RabbitMQAuditConsumer
from run_agent import _should_start_background_consumer


class FakeRawConnection:
    """模拟底层MySQL连接。"""

    def __init__(self, identifier: int) -> None:
        self.identifier = identifier
        self.open = True
        self.ping_calls: list[bool] = []
        self.close_calls = 0

    def ping(self, reconnect: bool = True) -> None:
        self.ping_calls.append(reconnect)

    def close(self) -> None:
        self.open = False
        self.close_calls += 1


class FakeConnectionBuilder:
    """统计底层建连次数。"""

    def __init__(self) -> None:
        self.calls = 0
        self.created: list[FakeRawConnection] = []

    def connect(self, **_kwargs):
        self.calls += 1
        connection = FakeRawConnection(self.calls)
        self.created.append(connection)
        return connection


class ImmediateExecutor:
    """同步执行的测试线程池。"""

    def submit(self, fn, *args, **kwargs):
        fn(*args, **kwargs)
        return SimpleNamespace()


class FakeRabbitConnection:
    """记录线程安全回调。"""

    def __init__(self) -> None:
        self.callbacks = []
        self.is_open = True

    def add_callback_threadsafe(self, callback) -> None:
        self.callbacks.append(callback)


class FakeChannel:
    """记录ack和nack。"""

    def __init__(self) -> None:
        self.is_open = True
        self.acks = []
        self.nacks = []
        self.exchange_declares = []
        self.queue_declares = []
        self.queue_binds = []
        self.qos_prefetch = None
        self.consumes = []

    def basic_ack(self, delivery_tag):
        self.acks.append(delivery_tag)

    def basic_nack(self, delivery_tag, requeue):
        self.nacks.append((delivery_tag, requeue))

    def exchange_declare(self, **kwargs):
        self.exchange_declares.append(kwargs)

    def queue_declare(self, **kwargs):
        self.queue_declares.append(kwargs)

    def queue_bind(self, **kwargs):
        self.queue_binds.append(kwargs)

    def basic_qos(self, prefetch_count):
        self.qos_prefetch = prefetch_count

    def basic_consume(self, **kwargs):
        self.consumes.append(kwargs)


class SuccessfulAuditJobService:
    """成功处理消息的假服务。"""

    def __init__(self) -> None:
        self.events = []

    def process_backend_event(self, event):
        self.events.append(event)


class ExhaustedAuditJobService:
    """模拟重试耗尽。"""

    def process_backend_event(self, _event):
        raise MaxRetriesExceededError("retry exhausted")


def build_rabbitmq_settings() -> RabbitMQSettings:
    return RabbitMQSettings(
        enabled=True,
        host="127.0.0.1",
        port=5672,
        username="guest",
        password="guest",
        vhost="/",
        exchange_name="ai.review.exchange",
        routing_key="ai.review",
        queue_name="ai.review.queue",
        prefetch_count=1,
        worker_concurrency=4,
        heartbeat=60,
        blocked_connection_timeout=30,
        requeue_on_failure=True,
        max_retries=3,
    )


class FakeBlockingConnection:
    def __init__(self, _parameters, channel: FakeChannel) -> None:
        self._channel = channel
        self.is_open = True

    def channel(self):
        return self._channel


def test_rabbitmq_consumer_declares_topology_before_consuming(monkeypatch):
    fake_channel = FakeChannel()

    class FakePikaModule:
        PlainCredentials = staticmethod(lambda username, password: (username, password))
        ConnectionParameters = staticmethod(lambda **kwargs: kwargs)

        @staticmethod
        def BlockingConnection(parameters):
            return FakeBlockingConnection(parameters, fake_channel)

    monkeypatch.setitem(sys.modules, "pika", FakePikaModule)

    consumer = RabbitMQAuditConsumer(
        rabbitmq_settings=build_rabbitmq_settings(),
        audit_job_service=SuccessfulAuditJobService(),
        executor=ImmediateExecutor(),
    )

    consumer._connect()

    assert fake_channel.exchange_declares == [
        {
            "exchange": "ai.review.exchange",
            "exchange_type": "direct",
            "durable": True,
            "auto_delete": False,
        }
    ]
    assert fake_channel.queue_declares == [
        {
            "queue": "ai.review.queue",
            "durable": True,
            "auto_delete": False,
        }
    ]
    assert fake_channel.queue_binds == [
        {
            "queue": "ai.review.queue",
            "exchange": "ai.review.exchange",
            "routing_key": "ai.review",
        }
    ]
    assert fake_channel.qos_prefetch == 4
    assert fake_channel.consumes[0]["queue"] == "ai.review.queue"


def test_mysql_connection_factory_reuses_idle_connection(monkeypatch):
    builder = FakeConnectionBuilder()
    fake_pymysql = SimpleNamespace(
        connect=builder.connect,
        cursors=SimpleNamespace(DictCursor=object()),
    )
    monkeypatch.setitem(sys.modules, "pymysql", fake_pymysql)

    settings = MySQLSettings(
        host="127.0.0.1",
        port=3306,
        user="root",
        password="",
        database="labelhub_agent",
        charset="utf8mb4",
        connect_timeout=5,
        pool_size=2,
        pool_timeout=1,
        pool_recycle_seconds=1800,
    )
    factory = MySQLConnectionFactory(settings)

    with factory.connect() as first:
        first_id = first.identifier

    with factory.connect() as second:
        second_id = second.identifier

    factory.close()

    assert first_id == second_id
    assert builder.calls == 1
    assert builder.created[0].ping_calls == [True]
    assert builder.created[0].close_calls == 1


def test_rabbitmq_consumer_schedules_ack_via_connection_callback():
    channel = FakeChannel()
    connection = FakeRabbitConnection()
    service = SuccessfulAuditJobService()
    consumer = RabbitMQAuditConsumer(
        rabbitmq_settings=build_rabbitmq_settings(),
        audit_job_service=service,
        executor=ImmediateExecutor(),
    )
    consumer._channel = channel
    consumer._connection = connection

    method = SimpleNamespace(delivery_tag="delivery-1")
    consumer._on_message(channel, method, None, b'{"taskItemId":"item-1","taskId":"task-1"}')

    assert service.events == [{"taskItemId": "item-1", "taskId": "task-1"}]
    assert channel.acks == []
    assert len(connection.callbacks) == 1

    connection.callbacks[0]()

    assert channel.acks == ["delivery-1"]
    assert channel.nacks == []


def test_rabbitmq_consumer_acks_retry_exhausted_messages():
    channel = FakeChannel()
    connection = FakeRabbitConnection()
    consumer = RabbitMQAuditConsumer(
        rabbitmq_settings=build_rabbitmq_settings(),
        audit_job_service=ExhaustedAuditJobService(),
        executor=ImmediateExecutor(),
    )
    consumer._channel = channel
    consumer._connection = connection

    method = SimpleNamespace(delivery_tag="delivery-2")
    consumer._on_message(channel, method, None, b'{"taskItemId":"item-2","taskId":"task-2"}')
    connection.callbacks[0]()

    assert channel.acks == ["delivery-2"]
    assert channel.nacks == []


def test_combined_launcher_skips_consumer_in_debug_reloader_parent(monkeypatch):
    monkeypatch.delenv("WERKZEUG_RUN_MAIN", raising=False)

    assert _should_start_background_consumer(debug=True) is False


def test_combined_launcher_allows_consumer_outside_reloader_parent(monkeypatch):
    monkeypatch.setenv("WERKZEUG_RUN_MAIN", "true")

    assert _should_start_background_consumer(debug=True) is True
    assert _should_start_background_consumer(debug=False) is True
