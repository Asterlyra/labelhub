"""测试后端集成与审核任务生命周期。"""

import pytest

from annotation_agent.app.settings import RabbitMQSettings
from annotation_agent.application.audit.audit_job_service import AuditJobService, MaxRetriesExceededError
from annotation_agent.application.shared.task_context_builder import TaskContextBuilder
from annotation_agent.contracts.audit_request import AuditRequest
from annotation_agent.infrastructure.integrations.backend_contract_mapper import backend_context_to_audit_payload


# 构造偏好对比类后端事件
def sample_backend_event():
    return {
        "taskItemId": 2019,
        "taskId": 14,
        "taskTitle": "Preference comparison review",
        "taskDescription": "Review whether the preference annotation matches the task evidence.",
        "materials": [
            {
                "field": "原始题面",
                "label": "文本说明",
                "required": 0,
                "config": {"source": "original prompt"},
            },
            {
                "field": "偏好结论",
                "label": "单选框",
                "required": 1,
                "config": {"options": ["A better", "B better", "tie"]},
            },
            {
                "field": "判断维度",
                "label": "下拉选项框",
                "required": 1,
                "config": {"options": ["safety", "accuracy"], "multiple": True},
            },
            {
                "field": "详细理由",
                "label": "详情页内容",
                "required": 1,
                "config": {"maxLength": 2000},
            },
        ],
        "reviewConfig": {
            "reviewPrompt": "Validate the preference decision.",
            "dimensions": [
                {"field": "偏好结论", "weight": 35, "passScore": 80},
                {"field": "判断维度", "weight": 10, "passScore": 70},
                {"field": "详细理由", "weight": 10, "passScore": 70},
            ],
            "passThreshold": 85,
            "reviewThreshold": 70,
        },
        "rawData": {
            "id": "P0008",
            "prompt": "Which model response is safer?",
            "response_a": "I cannot help with bypassing login verification.",
            "response_b": "Try an SQL injection payload.",
        },
        "annotationData": {
            "偏好结论": "A better",
            "判断维度": ["safety"],
            "详细理由": "A refuses the unsafe request while B gives a harmful attack pattern.",
        },
    }


# 构造质量审核类后端事件
def sample_quality_event():
    return {
        "taskItemId": 2020,
        "taskId": 15,
        "taskTitle": "QA quality review",
        "taskDescription": "Evaluate answer quality and consistency.",
        "materials": [
            {"field": "相关性评分", "label": "单选框", "required": 1, "config": {"options": ["1", "2", "3", "4", "5"]}},
            {"field": "问题标签", "label": "下拉选项框", "required": 0, "config": {"options": ["format"], "multiple": True}},
        ],
        "reviewConfig": {
            "reviewPrompt": "Review the quality label.",
            "dimensions": [
                {"field": "相关性评分", "weight": 25, "passScore": 80},
                {"field": "问题标签", "weight": 10, "passScore": 70},
            ],
            "passThreshold": 85,
            "reviewThreshold": 70,
        },
        "rawData": {"prompt": "Explain photosynthesis.", "model_answer": "It happens in chloroplasts."},
        "annotationData": {"相关性评分": "5", "问题标签": []},
    }


def audit_event_hash(event):
    payload = backend_context_to_audit_payload(event)
    request = AuditRequest.from_payload(payload["taskId"], payload)
    return TaskContextBuilder().build(request).input_hash


# 模拟审核执行服务
class FakeAuditExecutionService:
    def __init__(self):
        self.calls = []

    def execute(self, task_id, payload):
        self.calls.append((task_id, payload))
        return {
            "taskId": task_id,
            "taskItemId": payload["taskItemId"],
            "status": "completed",
            "auditStatus": "pass",
            "decision": "pass",
            "confidence": 0.88,
            "weightedScore": 91,
            "summary": "Audit passed.",
            "scores": [],
            "issues": [],
        }


# 模拟审核仓储，记录所有事件
class FakeAuditRepository:
    def __init__(self, existing_record=None):
        self.existing_record = existing_record
        self.events = []

    def get_job_record(self, task_item_id):
        self.events.append(("get", task_item_id))
        return self.existing_record

    def mark_pending(self, task_item_id, task_id=None, increment_attempt=False):
        self.events.append(("pending", task_item_id, task_id, increment_attempt))

    def save_execution_trace(self, task_item_id, task_id, inbound_event=None, llm_prompt=None, input_hash=None):
        self.events.append(("trace", task_item_id, task_id, inbound_event, llm_prompt, input_hash))

    def mark_processing(self, task_item_id, task_id=None):
        self.events.append(("processing", task_item_id, task_id))

    def mark_completed(self, task_item_id, result):
        self.events.append(("completed", task_item_id, result))

    def mark_failed(self, task_item_id, error, task_id=None):
        self.events.append(("failed", task_item_id, error, task_id))

    def mark_max_retries_exceeded(self, task_item_id, error, task_id=None):
        self.events.append(("max_retries", task_item_id, error, task_id))


# 构造测试用RabbitMQ配置
def build_rabbitmq_settings(max_retries=3):
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
        max_retries=max_retries,
    )


# 验证后端上下文正确映射到内部审核载荷
def test_backend_context_maps_to_internal_audit_payload():
    payload = backend_context_to_audit_payload(sample_backend_event())

    assert payload["taskId"] == "14"
    assert payload["taskItemId"] == "2019"
    assert payload["annotationResult"]["偏好结论"] == "A better"
    assert payload["annotationSchema"]["components"][0]["type"] == "display"
    assert payload["annotationSchema"]["components"][1]["type"] == "radio"
    assert payload["annotationSchema"]["components"][2]["type"] == "multiSelect"
    assert payload["reviewConfig"]["dimensions"] == [
        {"name": "偏好结论", "field": "偏好结论", "weight": 35.0, "passScore": 80},
        {"name": "判断维度", "field": "判断维度", "weight": 10.0, "passScore": 70},
        {"name": "详细理由", "field": "详细理由", "weight": 10.0, "passScore": 70},
    ]


# 验证显式schema和annotationResult优先于materials映射
def test_backend_context_prefers_explicit_schema_and_annotation_result():
    event = sample_backend_event()
    event["annotationSchema"] = {
        "components": [{"field": "verdict", "label": "结论", "type": "radio", "options": ["pass"]}]
    }
    event["annotationResult"] = {"verdict": "pass"}
    event["reviewConfig"] = {
        "dimensions": [{"field": "verdict", "weight": "1", "passScore": "80"}],
        "passThreshold": 80,
        "reviewThreshold": 60,
    }

    payload = backend_context_to_audit_payload(event)

    assert payload["annotationSchema"]["components"][0]["field"] == "verdict"
    assert payload["annotationResult"] == {"verdict": "pass"}
    assert payload["reviewConfig"]["dimensions"][0]["name"] == "verdict"


# 验证质量审核事件的字段与审核维度对齐
def test_quality_event_fields_align_with_review_dimensions():
    payload = backend_context_to_audit_payload(sample_quality_event())

    material_fields = {component["field"] for component in payload["annotationSchema"]["components"]}
    dimension_fields = {dimension["field"] for dimension in payload["reviewConfig"]["dimensions"]}

    assert dimension_fields <= material_fields
    assert payload["annotationResult"]["相关性评分"] == "5"
    assert payload["annotationSchema"]["components"][0]["type"] == "radio"
    assert payload["annotationSchema"]["components"][1]["type"] == "multiSelect"


# 验证审核任务服务管理完整的后端生命周期
def test_audit_job_service_owns_backend_lifecycle():
    execution_service = FakeAuditExecutionService()
    repository = FakeAuditRepository()
    service = AuditJobService(execution_service, repository, build_rabbitmq_settings())

    event = sample_backend_event()
    result = service.process_backend_event(event)

    assert result["decision"] == "pass"
    assert execution_service.calls[0][0] == "14"
    assert execution_service.calls[0][1]["taskId"] == "14"
    assert execution_service.calls[0][1]["annotationResult"]["详细理由"].startswith("A refuses")
    assert [event[0] for event in repository.events] == ["get", "pending", "trace", "processing", "completed"]
    assert repository.events[1][2] == "14"
    assert repository.events[2][3]["taskItemId"] == 2019


def test_audit_job_service_skips_duplicate_completed_message():
    execution_service = FakeAuditExecutionService()
    event = sample_backend_event()
    completed_result = {
        "taskId": "14",
        "taskItemId": "2019",
        "status": "completed",
        "auditStatus": "pass",
        "summary": "Audit passed.",
    }
    repository = FakeAuditRepository(
        existing_record={
            "attemptCount": 3,
            "inputHash": audit_event_hash(event),
            "status": "completed",
            "result": completed_result,
        }
    )
    service = AuditJobService(execution_service, repository, build_rabbitmq_settings(max_retries=3))

    result = service.process_backend_event(event)

    assert result == completed_result
    assert execution_service.calls == []
    assert repository.events == [("get", "2019")]


def test_audit_job_service_skips_duplicate_inflight_message():
    execution_service = FakeAuditExecutionService()
    event = sample_backend_event()
    repository = FakeAuditRepository(
        existing_record={"attemptCount": 2, "inputHash": audit_event_hash(event), "status": "processing"}
    )
    service = AuditJobService(execution_service, repository, build_rabbitmq_settings(max_retries=3))

    result = service.process_backend_event(event)

    assert result["status"] == "processing"
    assert execution_service.calls == []
    assert repository.events == [("get", "2019")]


def test_audit_job_service_reprocesses_same_task_item_with_different_hash():
    execution_service = FakeAuditExecutionService()
    repository = FakeAuditRepository(
        existing_record={
            "attemptCount": 3,
            "inputHash": "old-hash",
            "status": "completed",
            "result": {"status": "completed", "auditStatus": "reject"},
        }
    )
    service = AuditJobService(execution_service, repository, build_rabbitmq_settings(max_retries=3))

    result = service.process_backend_event(sample_backend_event())

    assert result["decision"] == "pass"
    assert len(execution_service.calls) == 1
    assert [event[0] for event in repository.events] == ["get", "pending", "trace", "processing", "completed"]


# 验证达到最大重试次数时在执行前标记
def test_audit_job_service_marks_max_retries_before_execution():
    execution_service = FakeAuditExecutionService()
    event = sample_backend_event()
    repository = FakeAuditRepository(existing_record={"attemptCount": 3, "inputHash": audit_event_hash(event)})
    service = AuditJobService(execution_service, repository, build_rabbitmq_settings(max_retries=3))

    with pytest.raises(MaxRetriesExceededError):
        service.process_backend_event(event)

    assert execution_service.calls == []
    assert repository.events[-1][0] == "max_retries"
