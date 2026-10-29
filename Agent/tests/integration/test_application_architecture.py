"""应用层架构测试，验证重构后的Agent结构正确。"""

import pytest

from annotation_agent.app.container import ServiceContainer
from annotation_agent.contracts.audit_request import AuditRequest
from annotation_agent.contracts.suggestion_request import SuggestionRequest
from annotation_agent.application.shared.task_context_builder import TaskContextBuilder
from annotation_agent.application.suggestion.suggestion_prompt_builder import SuggestionPromptBuilder
from annotation_agent.domain.audit.audit_config import AuditConfig
from annotation_agent.domain.audit.audit_result_factory import AuditResultFactory
from annotation_agent.domain.audit.decision_policy import AuditDecisionPolicy
from annotation_agent.infrastructure.integrations.backend_contract_mapper import backend_context_to_audit_payload


# 构造动态审核载荷，支持自定义review_prompt
def dynamic_payload(review_prompt: str = "") -> dict:
    return {
        "projectId": "demo-project",
        "taskTitle": "Answer quality audit",
        "taskDescription": "Review whether the answer is relevant, accurate, and safe.",
        "rawData": {
            "prompt": "Where does photosynthesis mainly happen?",
            "model_answer": "It mainly happens in chloroplasts.",
            "reference": "chloroplasts",
        },
        "annotationSchema": {
            "components": [
                {
                    "field": "accuracy_score",
                    "label": "Accuracy Score",
                    "type": "radio",
                    "required": True,
                    "options": [1, 2, 3, 4, 5],
                },
            ],
        },
        "annotationResult": {"accuracy_score": 5},
        "reviewConfig": {
            "reviewPrompt": review_prompt,
            "dimensions": [
                {"field": "accuracy_score", "weight": 1.0, "passScore": 85},
            ],
            "passThreshold": 85,
            "reviewThreshold": 70,
        },
    }


# 构造经后端映射器转换的载荷
def mapped_backend_payload() -> dict:
    return backend_context_to_audit_payload(
        {
            "taskItemId": 2019,
            "taskId": 14,
            "taskTitle": "Backend mapped review",
            "taskDescription": "Validate mapped backend payloads.",
            "materials": [
                {"field": "source_text", "label": "文本说明", "required": 0, "config": {"source": "prompt"}},
                {"field": "verdict", "label": "单选框", "required": 1, "config": {"options": ["pass", "reject"]}},
            ],
            "reviewConfig": {
                "reviewPrompt": "Audit the mapped payload.",
                "dimensions": [{"field": "verdict", "weight": 1, "passScore": 85}],
                "passThreshold": 85,
                "reviewThreshold": 70,
            },
            "rawData": {"prompt": "demo", "reference": "demo"},
            "annotationData": {"verdict": "pass"},
        }
    )


def suggestion_payload(task_id: str) -> dict:
    return {
        "taskId": task_id,
        "taskItemId": "suggestion-item-1001",
        "taskTitle": "Summary quality task",
        "taskDescription": "Review whether the summary covers key information accurately.",
        "materials": [
            {
                "field": "原始题面",
                "label": "文本说明",
                "required": 0,
                "config": {"source": "summary task source text"},
            },
            {
                "field": "相关性评分",
                "label": "单选框",
                "required": 1,
                "config": {"options": ["1", "2", "3", "4", "5"]},
            },
            {
                "field": "问题类型标签",
                "label": "下拉选项框",
                "required": 0,
                "config": {"options": ["信息缺失", "事实错误"], "multiple": True},
            },
        ],
        "rawData": {
            "prompt": "Summarize the following meeting notes.",
            "model_answer": "The meeting confirmed the Q3 release plan.",
            "reference": "Cover all key decisions and deadlines.",
        },
    }


# 构建测试用审核执行服务
def build_audit_service(settings, audit_repo):
    container = ServiceContainer(settings=settings, audit_repository=audit_repo)
    return container.audit_execution_service


# 验证审核上下文构建器使用后端载荷直通模式
def test_audit_context_builder_uses_backend_payload_passthrough(settings, audit_repo):
    service = build_audit_service(settings, audit_repo)
    request = AuditRequest.from_payload("v1-task", dynamic_payload())

    context = service.context_builder.build(request)

    assert context.schema_fields[0].field == "accuracy_score"
    assert context.validation_issues == []
    assert context.dimensions == [{"field": "accuracy_score", "weight": 1.0, "passScore": 85}]
    assert context.evidence_bundle.task_kind == "backend_payload_passthrough"
    assert context.evidence_bundle.items == []
    assert context.prompt_messages.system_prompt.startswith("你是LabelHub AI审核智能体。")
    assert "审核指令规范：" not in context.prompt_messages.system_prompt
    assert "审核指令规范：" in context.prompt_messages.user_prompt
    assert "维度覆盖要求：" in context.prompt
    assert "accuracy_score" in context.prompt
    assert "passThreshold" not in context.prompt
    assert "{review_prompt}" not in context.prompt


def test_input_hash_ignores_task_and_task_item_ids():
    builder = TaskContextBuilder()
    audit_payload = dynamic_payload()
    audit_payload["taskItemId"] = "audit-item-1"
    first_audit = builder.build(AuditRequest.from_payload("audit-task-1", audit_payload))
    audit_payload["taskItemId"] = "audit-item-2"
    second_audit = builder.build(AuditRequest.from_payload("audit-task-2", audit_payload))

    suggestion_first = suggestion_payload("suggestion-task-1")
    suggestion_second = suggestion_payload("suggestion-task-2")
    suggestion_second["taskItemId"] = "suggestion-item-2002"

    first_suggestion = builder.build(SuggestionRequest.from_payload(suggestion_first))
    second_suggestion = builder.build(SuggestionRequest.from_payload(suggestion_second))

    assert first_audit.input_hash == second_audit.input_hash
    assert first_suggestion.input_hash == second_suggestion.input_hash


# 验证审核上下文构建器将review_prompt注入用户模板
def test_audit_context_builder_injects_review_prompt_into_user_template(settings, audit_repo):
    service = build_audit_service(settings, audit_repo)
    inline_prompt = "Focus on whether the accuracy_score field matches the evidence."
    request = AuditRequest.from_payload("inline-prompt-task", dynamic_payload(inline_prompt))

    context = service.context_builder.build(request)

    assert context.prompt.startswith("你是LabelHub AI审核智能体。")
    assert inline_prompt not in context.prompt_messages.system_prompt
    assert inline_prompt in context.prompt_messages.user_prompt
    assert inline_prompt in context.prompt
    assert '"accuracy_score": 5' in context.prompt


# 验证审核配置正确读取review_prompt
def test_audit_config_reads_review_prompt():
    config = AuditConfig.from_dict(
        {
            "reviewPrompt": "Audit according to the rubric.",
            "dimensions": [
                {"field": "accuracy_score", "weight": 1.0, "passScore": 85},
            ],
        }
    )

    assert config.review_prompt == "Audit according to the rubric."
    assert config.pass_threshold == 85
    assert config.review_threshold == 70


# 验证审核请求必须包含reviewConfig
def test_audit_request_requires_review_config():
    with pytest.raises(ValueError, match="missing required audit payload field: reviewConfig"):
        AuditRequest.from_payload(
            "bad-task",
            {
                "projectId": "demo-project",
                "rawData": {},
                "annotationSchema": {"components": []},
                "annotationResult": {},
            },
        )


# 验证结果工厂输出最小化的结果字段
def test_result_factory_outputs_minimal_result_fields(settings, audit_repo):
    service = build_audit_service(settings, audit_repo)
    context = service.context_builder.build(AuditRequest.from_payload("result-task", dynamic_payload()))
    model_result = {
        "scores": [
            {"dimension": dimension["field"], "score": 90, "reason": "Test pass."}
            for dimension in context.dimensions
        ],
        "issues": [],
        "summary": "Audit passed.",
        "confidence": 0.9,
    }
    decision = AuditDecisionPolicy().decide(model_result, context.config, [])

    result = AuditResultFactory().build_success_result(context, model_result, decision).to_dict()

    assert "taskRoute" not in result
    assert "trace" not in result
    assert "toolOutputs" not in result
    assert "inputSnapshot" not in result
    assert "promptTemplateId" not in result
    assert result["status"] == "completed"
    assert result["auditStatus"] == "pass"
    assert result["weightedScore"] > 0


# 验证结果工厂输出LLM失败结果
def test_result_factory_outputs_llm_failure_result(settings, audit_repo):
    service = build_audit_service(settings, audit_repo)
    context = service.context_builder.build(AuditRequest.from_payload("failure-task", dynamic_payload()))

    result = AuditResultFactory().build_llm_failure_result(context).to_dict()

    assert result["status"] == "completed"
    assert result["auditStatus"] == "reject"
    assert "trace" not in result


# 验证后端载荷提示词正确解析所有占位符
def test_backend_payload_prompt_resolves_placeholders(settings, audit_repo):
    service = build_audit_service(settings, audit_repo)
    payload = mapped_backend_payload()
    context = service.context_builder.build(AuditRequest.from_payload(payload["taskId"], payload))

    assert "{{" not in context.prompt
    assert "}}" not in context.prompt
    for placeholder in (
        "{review_prompt}",
        "{raw_data}",
        "{task_title}",
        "{task_description}",
        "{annotation_schema}",
        "{annotation_result}",
        "{review_config}",
        "{validation_issues}",
        "{dimension_coverage_rule}",
        "{evidence}",
    ):
        assert placeholder not in context.prompt
    assert "维度覆盖要求：" in context.prompt
    assert "scores数组必须覆盖所有列出的维度" in context.prompt
    for dimension in payload["reviewConfig"]["dimensions"]:
        assert dimension["field"] in context.prompt


# 验证审核执行服务在LLM调用前持久化提示词
def test_audit_execution_service_persists_prompt_before_llm_invocation(settings):
    class PromptCaptureRepo:
        def __init__(self):
            self.prompt_calls = []

        def get_result_by_input_hash(self, task_id, input_hash):
            return None

        def save_execution_trace(self, task_item_id, task_id, inbound_event=None, llm_prompt=None, input_hash=None):
            self.prompt_calls.append(
                {
                    "task_item_id": task_item_id,
                    "task_id": task_id,
                    "inbound_event": inbound_event,
                    "llm_prompt": llm_prompt,
                    "input_hash": input_hash,
                }
            )

        def save_result(self, result):
            return result.to_dict()

    class FakeLLMGateway:
        def __init__(self, repo):
            self.repo = repo

        def invoke_audit(self, prompt, dimensions, audit_context=None, prompt_messages=None):
            assert self.repo.prompt_calls
            assert self.repo.prompt_calls[-1]["llm_prompt"] == prompt
            return {
                "scores": [
                    {"dimension": dimension["field"], "score": 90, "reason": "Test pass"}
                    for dimension in dimensions
                ],
                "issues": [],
                "summary": "Audit passed",
                "confidence": 0.9,
            }

    repo = PromptCaptureRepo()
    container = ServiceContainer(settings=settings, audit_repository=repo, llm_gateway=FakeLLMGateway(repo))
    service = container.audit_execution_service

    payload = dynamic_payload()
    payload["taskItemId"] = "task-item-ctx"
    result = service.execute("task-ctx", payload)

    assert result["taskItemId"] == "task-item-ctx"
    assert repo.prompt_calls[0]["task_item_id"] == "task-item-ctx"
    assert repo.prompt_calls[0]["task_id"] == "task-ctx"
    assert repo.prompt_calls[0]["inbound_event"] is None
    assert repo.prompt_calls[0]["input_hash"]
    assert "accuracy_score" in repo.prompt_calls[0]["llm_prompt"]


def test_suggestion_prompt_builder_splits_system_and_user_messages(settings):
    container = ServiceContainer(settings=settings)
    request = SuggestionRequest.from_payload(
        suggestion_payload("suggestion-template-task"),
    )

    context = container.task_context_builder.build(request)
    prompt_messages = SuggestionPromptBuilder(container.prompt_template_repository).build(request, context)

    assert prompt_messages.system_prompt.startswith("你是LabelHub AI标注建议智能体。")
    assert '"annotationData": {' in prompt_messages.system_prompt
    assert "原始数据：" not in prompt_messages.system_prompt
    assert "任务ID：" in prompt_messages.user_prompt
    assert "任务子项ID：" in prompt_messages.user_prompt
    assert "原始数据：" in prompt_messages.user_prompt
    assert "标注模式：" in prompt_messages.user_prompt
    assert "可写字段：" in prompt_messages.user_prompt
    assert "相关性评分" in prompt_messages.user_prompt
    for placeholder in (
        "{task_id}",
        "{task_item_id}",
        "{task_title}",
        "{task_description}",
        "{raw_data}",
        "{annotation_schema}",
        "{writable_fields}",
    ):
        assert placeholder not in prompt_messages.to_text()
