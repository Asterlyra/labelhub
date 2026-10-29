"""测试共享配置与夹具定义。

提供内存仓储、测试客户端、LLM配置等测试基础设施。
"""

import os
import sys
from datetime import datetime, timezone
from pathlib import Path
from uuid import uuid4

import pytest


# Agent项目根目录，用于将项目加入sys.path
AGENT_DIR = Path(__file__).resolve().parents[1]

if str(AGENT_DIR) not in sys.path:
    sys.path.insert(0, str(AGENT_DIR))

from annotation_agent import create_app
from annotation_agent.app.container import ServiceContainer
from annotation_agent.app.settings import load_settings
from annotation_agent.domain.shared.enums import TaskStatus


# 内存审核仓储，用于测试时替代MySQL
class InMemoryAuditRepository:
    """内存审核结果仓储，测试专用。"""
    def __init__(self):
        # 存储审核结果，key为task_item_id
        self.results = {}
        # 存储执行追踪记录
        self.execution_traces = []

    def save_result(self, result):
        payload = result.to_dict() if hasattr(result, "to_dict") else dict(result)
        key = payload.get("taskItemId") or payload.get("taskId")
        self.results[key] = dict(payload)
        return dict(payload)

    def get_result(self, task_item_id):
        payload = self.results.get(task_item_id)
        return dict(payload) if payload else None

    def list_results_by_task_id(self, task_id):
        rows = []
        for payload in self.results.values():
            if str(payload.get("taskId") or "") != str(task_id):
                continue
            rows.append(
                {
                    "taskItemId": str(payload.get("taskItemId") or ""),
                    "taskId": str(payload.get("taskId") or ""),
                    "status": payload.get("status") or "",
                    "auditStatus": payload.get("auditStatus") or "",
                }
            )
        return sorted(rows, key=lambda item: item["taskItemId"])

    def get_result_by_input_hash(self, task_item_id, input_hash):
        payload = self.results.get(task_item_id)
        if (
            payload
            and payload.get("inputHash") == input_hash
            and payload.get("status") == TaskStatus.COMPLETED
            and payload.get("scores")
        ):
            return dict(payload)
        return None

    def save_execution_trace(self, task_item_id, task_id, inbound_event=None, llm_prompt=None, input_hash=None):
        self.execution_traces.append(
            {
                "task_item_id": task_item_id,
                "task_id": task_id,
                "inbound_event": inbound_event,
                "llm_prompt": llm_prompt,
                "input_hash": input_hash,
            }
        )

    def get_job_record(self, task_item_id):
        payload = self.results.get(task_item_id)
        return dict(payload) if payload else None

    def mark_pending(self, task_item_id, task_id=None, increment_attempt=False):
        payload = self.results.setdefault(
            task_item_id,
            {"taskItemId": task_item_id, "taskId": task_id or task_item_id, "status": TaskStatus.PENDING, "attemptCount": 0},
        )
        if increment_attempt:
            payload["attemptCount"] += 1
        payload["status"] = TaskStatus.PENDING
        return dict(payload)

    def mark_processing(self, task_item_id, task_id=None):
        payload = self.results.setdefault(
            task_item_id,
            {"taskItemId": task_item_id, "taskId": task_id or task_item_id, "status": TaskStatus.PENDING},
        )
        payload["status"] = TaskStatus.PROCESSING
        return dict(payload)

    def mark_completed(self, task_item_id, result):
        payload = self.results.setdefault(task_item_id, {"taskItemId": task_item_id})
        payload["status"] = TaskStatus.COMPLETED
        payload["auditStatus"] = result.get("auditStatus", result.get("status", ""))
        payload["mediaResult"] = result.get("mediaResult")
        payload["result"] = dict(result)
        return dict(payload)

    def mark_failed(self, task_item_id, error, task_id=None):
        payload = self.results.setdefault(task_item_id, {"taskItemId": task_item_id, "taskId": task_id or task_item_id})
        payload["status"] = TaskStatus.FAILED
        payload["lastError"] = error
        return dict(payload)

    def mark_max_retries_exceeded(self, task_item_id, error, task_id=None):
        payload = self.results.setdefault(task_item_id, {"taskItemId": task_item_id, "taskId": task_id or task_item_id})
        payload["status"] = TaskStatus.MAX_RETRIES_EXCEEDED
        payload["lastError"] = error
        return dict(payload)


# 内存建议仓储，用于测试时替代MySQL
class InMemorySuggestionRepository:
    """内存建议任务仓储，测试专用。"""

    def __init__(self):
        # 存储建议任务，key为suggestion_id
        self.jobs = {}

    def create_job(self, suggestion_id, task_id, task_item_id, input_hash, request_payload):
        payload = {
            "suggestionId": suggestion_id,
            "taskId": task_id,
            "taskItemId": task_item_id,
            "status": TaskStatus.PENDING,
            "inputHash": input_hash,
            "requestPayload": dict(request_payload),
            "annotationData": None,
            "llmPrompt": "",
            "rawModelOutput": "",
            "attemptCount": 0,
            "lastError": "",
        }
        self.jobs[suggestion_id] = payload
        return dict(payload)

    def get_job(self, suggestion_id):
        payload = self.jobs.get(suggestion_id)
        return dict(payload) if payload else None

    def get_job_for_task_item(self, suggestion_id, task_item_id):
        payload = self.jobs.get(suggestion_id)
        if not payload or payload.get("taskItemId") != task_item_id:
            return None
        return dict(payload)

    def get_completed_job_by_task_item_hash(self, task_item_id, input_hash):
        for payload in self.jobs.values():
            if (
                payload.get("taskItemId") == task_item_id
                and payload.get("inputHash") == input_hash
                and payload.get("status") == TaskStatus.COMPLETED
            ):
                return dict(payload)
        return None

    def get_completed_job_by_input_hash(self, input_hash):
        for payload in reversed(list(self.jobs.values())):
            if payload.get("inputHash") == input_hash and payload.get("status") == TaskStatus.COMPLETED:
                return dict(payload)
        return None

    def mark_processing(self, suggestion_id, llm_prompt):
        payload = self.jobs.get(suggestion_id)
        if not payload:
            return None
        payload["status"] = TaskStatus.PROCESSING
        payload["llmPrompt"] = llm_prompt
        payload["attemptCount"] += 1
        payload["lastError"] = ""
        return dict(payload)

    def mark_completed(self, suggestion_id, annotation_data, raw_model_output, llm_prompt=None):
        payload = self.jobs.get(suggestion_id)
        if not payload:
            return None
        payload["status"] = TaskStatus.COMPLETED
        payload["annotationData"] = dict(annotation_data)
        payload["rawModelOutput"] = raw_model_output
        if llm_prompt is not None:
            payload["llmPrompt"] = llm_prompt
        payload["lastError"] = ""
        return dict(payload)

    def mark_failed(self, suggestion_id, error, raw_model_output="", llm_prompt=None):
        payload = self.jobs.get(suggestion_id)
        if not payload:
            return None
        payload["status"] = TaskStatus.FAILED
        payload["rawModelOutput"] = raw_model_output
        if llm_prompt is not None:
            payload["llmPrompt"] = llm_prompt
        payload["lastError"] = error
        return dict(payload)


# 自动配置测试用LLM（默认使用模拟模式）
@pytest.fixture(autouse=True)
def configure_test_llm(monkeypatch):
    use_real_llm = os.getenv("AI_AUDIT_TEST_REAL_LLM", "false").lower() == "true"
    if use_real_llm:
        monkeypatch.setenv("AI_AUDIT_USE_MOCK", "false")
        return
    monkeypatch.setenv("AI_AUDIT_PROVIDER", "mock")
    monkeypatch.setenv("AI_AUDIT_USE_MOCK", "true")


# 加载应用配置（受LLM夹具影响）
@pytest.fixture()
def settings(configure_test_llm):
    return load_settings()


# 内存审核仓储夹具
@pytest.fixture()
def audit_repo():
    return InMemoryAuditRepository()


# 内存建议仓储夹具
@pytest.fixture()
def suggestion_repo():
    return InMemorySuggestionRepository()


# 终端日志输出夹具，用于测试时向stdout打印带时间戳的调试信息
@pytest.fixture()
def terminal_log():
    def _log(message: str) -> None:
        timestamp = datetime.now(timezone.utc).isoformat(timespec="seconds")
        sys.__stdout__.write(f"[agent-test {timestamp}] {message}\n")
        sys.__stdout__.flush()

    return _log


# 生成唯一运行ID，用于测试数据隔离
@pytest.fixture()
def run_id() -> str:
    return uuid4().hex[:8]


# Flask测试客户端夹具，组装完整的测试应用
@pytest.fixture()
def client(settings, terminal_log, audit_repo, suggestion_repo, monkeypatch):
    token = "test-only-" + "x" * 32
    monkeypatch.setenv("LABELHUB_AGENT_TOKEN", token)
    # 如果使用真实LLM但未配置API密钥，则跳过测试
    use_real_llm = os.getenv("AI_AUDIT_TEST_REAL_LLM", "false").lower() == "true"
    if use_real_llm and not settings.llm.api_key:
        pytest.skip(
            "Real LLM tests require AI_AUDIT_API_KEY. "
            "Omit AI_AUDIT_TEST_REAL_LLM to use default mock tests."
        )

    terminal_log(f"LLM provider: {settings.llm.provider}")
    terminal_log(f"LLM base URL configured: {bool(settings.llm.base_url)}")
    terminal_log(f"LLM API key configured: {bool(settings.llm.api_key)}")
    terminal_log(f"LLM use mock: {settings.llm.use_mock}")
    terminal_log(f"LLM model: {settings.llm.model_name}")

    container = ServiceContainer(
        settings=settings,
        audit_repository=audit_repo,
        suggestion_repository=suggestion_repo,
        suggestion_run_async=False,
    )
    app = create_app(container=container)
    app.config.update(TESTING=True)
    test_client = app.test_client()
    test_client.environ_base["HTTP_X_LABELHUB_AGENT_TOKEN"] = token
    return test_client
