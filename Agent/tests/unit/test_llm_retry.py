"""测试LLM重试机制与结构化输出。"""

import json
import sys
from types import SimpleNamespace

import annotation_agent.infrastructure.llm.shared.openai_compatible_client as openai_client_module
from annotation_agent.app.settings import LLMSettings
from annotation_agent.domain.shared.task_schema import SchemaField
from annotation_agent.infrastructure.llm.llm_service import LLMService
from annotation_agent.infrastructure.llm.audit.schema import AUDIT_RESULT_SCHEMA
from annotation_agent.infrastructure.llm.shared.prompt_messages import PromptContentPart, PromptMessages
from annotation_agent.infrastructure.llm.suggestion.output_validator import SuggestionOutputValidator
from annotation_agent.infrastructure.llm.suggestion.schema import build_suggestion_structured_schema


# 测试用审核维度配置
DIMENSIONS = [
    {"field": "relevance", "weight": 0.5, "passScore": 80},
    {"field": "accuracy", "weight": 0.5, "passScore": 85},
]


# 模拟LLM返回的消息对象
class FakeMessage:
    def __init__(self, content):
        self.content = content


# 模拟结构化输出的可运行对象
class FakeStructuredRunnable:
    def __init__(self, owner):
        self.owner = owner

    def invoke(self, prompt):
        self.owner.__class__.prompts.append(prompt)
        output = self.owner.__class__._next_output()
        if isinstance(output, Exception):
            raise output
        if isinstance(output, dict):
            return output
        raise json.JSONDecodeError("Expecting value", "", 0)


# 模拟ChatOpenAI客户端，用于测试LLM重试逻辑
class FakeChatOpenAI:
    outputs = []
    prompts = []
    structured_calls = []
    output_index = 0

    def __init__(self, **_kwargs):
        pass

    @classmethod
    def _next_output(cls):
        output = cls.outputs[cls.output_index]
        cls.output_index += 1
        return output

    def invoke(self, prompt, **_kwargs):
        self.__class__.prompts.append(prompt)
        output = self.__class__._next_output()
        if isinstance(output, Exception):
            raise output
        if isinstance(output, dict):
            return FakeMessage(json.dumps(output, ensure_ascii=False))
        return FakeMessage(output)

    def with_structured_output(
        self,
        schema=None,
        *,
        method="json_schema",
        include_raw=False,
        strict=None,
        tools=None,
        **kwargs,
    ):
        self.__class__.structured_calls.append(
            {
                "schema": schema,
                "method": method,
                "include_raw": include_raw,
                "strict": strict,
                "tools": tools,
                "kwargs": kwargs,
            }
        )
        return FakeStructuredRunnable(self)


# 构建测试用LLM服务实例
def build_service(base_url="http://example.test"):
    return LLMService(
        LLMSettings(
            provider="openai_compatible",
            base_url=base_url,
            api_key="test-key",
            model_name="gpt-4o-mini",
            temperature=0,
            use_mock=False,
        )
    )


# 安装模拟ChatOpenAI到测试环境
def install_fake_chat(monkeypatch, outputs):
    FakeChatOpenAI.outputs = outputs
    FakeChatOpenAI.prompts = []
    FakeChatOpenAI.structured_calls = []
    FakeChatOpenAI.output_index = 0
    openai_client_module.reset_structured_output_support_cache()
    monkeypatch.setitem(sys.modules, "langchain_openai", SimpleNamespace(ChatOpenAI=FakeChatOpenAI))


# 构造合法的审核输出
def valid_output():
    return {
        "scores": [
            {"dimension": "relevance", "score": 90, "reason": "Answer stays on topic."},
            {"dimension": "accuracy", "score": 86, "reason": "Facts are mostly correct."},
        ],
        "summary": "Audit passed.",
        "confidence": 0.89,
    }


# 验证LLM结构化输出成功场景
def test_llm_structured_output_success(monkeypatch):
    install_fake_chat(monkeypatch, [valid_output()])
    service = build_service()

    model_result = service.invoke_audit("Original prompt", DIMENSIONS, {})

    assert model_result is not None
    assert model_result["summary"] == "Audit passed."
    assert FakeChatOpenAI.structured_calls[0]["schema"] is AUDIT_RESULT_SCHEMA
    assert FakeChatOpenAI.structured_calls[0]["method"] == "json_schema"
    assert FakeChatOpenAI.structured_calls[0]["strict"] is True


def test_llm_structured_output_transport_retry_succeeds(monkeypatch):
    install_fake_chat(monkeypatch, [Exception("temporary upstream timeout"), valid_output()])
    service = build_service()

    model_result = service.invoke_audit("Original prompt", DIMENSIONS, {})

    assert model_result is not None
    assert model_result["summary"] == "Audit passed."
    assert len(FakeChatOpenAI.prompts) == 2


# 验证LLM输出格式错误时自动重试
def test_llm_structured_output_retry_on_error(monkeypatch):
    install_fake_chat(monkeypatch, ["bad-format", "also-bad", valid_output()])
    service = build_service()

    model_result = service.invoke_audit("Original prompt", DIMENSIONS, {})

    assert model_result is not None
    assert model_result["summary"] == "Audit passed."
    assert len(FakeChatOpenAI.prompts) == 3


# 验证回退模式接受字典输出
def test_llm_fallback_accepts_dict_output(monkeypatch):
    install_fake_chat(monkeypatch, ["bad-format", valid_output()])
    service = build_service()

    model_result = service.invoke_audit("Original prompt", DIMENSIONS, {})

    assert model_result is not None
    assert model_result["summary"] == "Audit passed."


# 验证回退模式注入审核专用JSON指令
def test_llm_fallback_injects_audit_specific_json_instruction(monkeypatch):
    install_fake_chat(
        monkeypatch,
        [Exception("response_format json_schema is not supported"), valid_output()],
    )
    service = build_service()

    model_result = service.invoke_audit("Original prompt", DIMENSIONS, {})

    assert model_result is not None
    fallback_prompt = FakeChatOpenAI.prompts[-1]
    assert "期望的JSON结构" in fallback_prompt
    assert "scores必须恰好覆盖以下维度各一次" in fallback_prompt
    assert "relevance" in fallback_prompt
    assert "accuracy" in fallback_prompt
    assert '"dimension": "字段名"' in fallback_prompt
    assert '"summary": "总体评价"' in fallback_prompt


def test_dashscope_compatible_mode_skips_structured_output_and_uses_json_instruction(monkeypatch):
    install_fake_chat(monkeypatch, [valid_output()])
    service = build_service(base_url="https://dashscope.aliyuncs.com/compatible-mode/v1")

    model_result = service.invoke_audit("Original prompt", DIMENSIONS, {})

    assert model_result is not None
    assert model_result["summary"] == "Audit passed."
    assert FakeChatOpenAI.structured_calls == []
    assert len(FakeChatOpenAI.prompts) == 1
    assert "期望的JSON结构" in FakeChatOpenAI.prompts[0]


def test_audit_generation_repairs_duplicate_and_malformed_scores_without_retry(monkeypatch):
    install_fake_chat(
        monkeypatch,
        [
            {
                "scores": [
                    {"dimension": "relevance", "score": 90, "reason": "Answer stays on topic."},
                    {"dimension": "relevance", "score": 90, "reason": "Answer stays on topic."},
                    {"dimension": "", "score": 0, "reason": "noise"},
                    {"dimension": "accuracy", "score": 86, "reason": "Facts are mostly correct."},
                ],
                "summary": "  Audit passed.  ",
                "confidence": "0.89",
            }
        ],
    )
    service = build_service()

    model_result = service.invoke_audit("Original prompt", DIMENSIONS, {})

    assert model_result is not None
    assert model_result["summary"] == "Audit passed."
    assert model_result["confidence"] == 0.89
    assert model_result["scores"] == [
        {"dimension": "relevance", "score": 90, "reason": "Answer stays on topic."},
        {"dimension": "accuracy", "score": 86, "reason": "Facts are mostly correct."},
    ]
    assert len(FakeChatOpenAI.prompts) == 1


# 验证消息模式使用system和human角色
def test_llm_message_mode_uses_system_and_human_roles(monkeypatch):
    install_fake_chat(monkeypatch, [valid_output()])
    service = build_service()
    prompt_messages = PromptMessages(
        system_prompt="System instructions",
        user_prompt="User payload",
    )

    model_result = service.invoke_audit(
        prompt_messages.to_text(),
        DIMENSIONS,
        {},
        prompt_messages=prompt_messages,
    )

    assert model_result is not None
    structured_prompt = FakeChatOpenAI.prompts[-1]
    assert structured_prompt == [
        ("system", "System instructions"),
        ("human", "User payload"),
    ]


def test_suggestion_generation_message_mode_uses_system_and_human_roles(monkeypatch):
    install_fake_chat(
        monkeypatch,
        [{"annotationData": {"quality": "1", "comment": "Looks good."}}],
    )
    service = build_service()
    prompt_messages = PromptMessages(
        system_prompt="Suggestion system",
        user_prompt="Suggestion user",
    )
    schema_fields = [
        SchemaField(field="quality", label="Quality", type="radio", required=True, options=["1", "2"]),
        SchemaField(field="comment", label="Comment", type="textarea", required=True),
    ]

    result, raw_output, llm_trace = service.invoke_generation(
        prompt=prompt_messages,
        output_validator=SuggestionOutputValidator(),
        validator_context=schema_fields,
        llm_context={},
        structured_schema=build_suggestion_structured_schema(schema_fields),
    )

    assert result is not None
    assert raw_output
    assert llm_trace["success"] is True
    assert FakeChatOpenAI.prompts[-1] == [
        ("system", "Suggestion system"),
        ("human", "Suggestion user"),
    ]


def test_suggestion_generation_retries_all_empty_output(monkeypatch):
    install_fake_chat(
        monkeypatch,
        [
            {"annotationData": {"detailHtml": "", "sellingPoints": []}},
            {"annotationData": {"detailHtml": "商品详情完整清晰。", "sellingPoints": ["发货快"]}},
        ],
    )
    service = build_service()
    prompt_messages = PromptMessages(
        system_prompt="Suggestion system",
        user_prompt="Suggestion user",
    )
    schema_fields = [
        SchemaField(field="detailHtml", label="Detail", type="textarea"),
        SchemaField(field="sellingPoints", label="Selling Points", type="multiSelect", options=["发货快", "价格低"]),
    ]

    result, raw_output, llm_trace = service.invoke_generation(
        prompt=prompt_messages,
        output_validator=SuggestionOutputValidator(),
        validator_context=schema_fields,
        llm_context={},
        structured_schema=build_suggestion_structured_schema(schema_fields),
    )

    assert result == {
        "annotationData": {
            "detailHtml": "商品详情完整清晰。",
            "sellingPoints": ["发货快"],
        }
    }
    assert json.loads(raw_output)["annotationData"]["detailHtml"] == "商品详情完整清晰。"
    assert llm_trace["attempts"] == 2
    assert llm_trace["success"] is True
    retry_prompt = FakeChatOpenAI.prompts[-1]
    assert "annotationData must include at least one meaningful value" in retry_prompt[1][1]


def test_suggestion_generation_repairs_text_max_length_locally_without_retry(monkeypatch):
    install_fake_chat(
        monkeypatch,
        [
            {
                "annotationData": {
                    "summary": "  This summary is longer than ten chars.  ",
                    "details": "ok",
                }
            }
        ],
    )
    service = build_service()
    prompt_messages = PromptMessages(
        system_prompt="Suggestion system",
        user_prompt="Suggestion user",
    )
    schema_fields = [
        SchemaField(field="summary", label="Summary", type="input", required=True, config={"maxLength": 10}),
        SchemaField(field="details", label="Details", type="textarea", required=False, config={"maxLength": 50}),
    ]

    result, raw_output, llm_trace = service.invoke_generation(
        prompt=prompt_messages,
        output_validator=SuggestionOutputValidator(),
        validator_context=schema_fields,
        llm_context={},
        structured_schema=build_suggestion_structured_schema(schema_fields),
    )

    assert result == {
        "annotationData": {
            "summary": "This summa",
            "details": "ok",
        }
    }
    assert json.loads(raw_output)["annotationData"]["summary"] == "This summa"
    assert llm_trace["attempts"] == 1
    assert llm_trace["success"] is True
    assert llm_trace["localRepairs"] == [
        {
            "attempt": 1,
            "actions": [
                "summary: trimmed surrounding whitespace",
                "summary: truncated text to maxLength 10",
            ],
        }
    ]
    assert len(FakeChatOpenAI.prompts) == 1


def test_suggestion_generation_repairs_duplicate_multi_choice_locally_without_retry(monkeypatch):
    install_fake_chat(
        monkeypatch,
        [
            {
                "annotationData": {
                    "tags": ["a", "a", "b"],
                    "comment": "ready",
                }
            }
        ],
    )
    service = build_service()
    prompt_messages = PromptMessages(
        system_prompt="Suggestion system",
        user_prompt="Suggestion user",
    )
    schema_fields = [
        SchemaField(field="tags", label="Tags", type="multiSelect", required=False, options=["a", "b"]),
        SchemaField(field="comment", label="Comment", type="textarea", required=False, config={"maxLength": 20}),
    ]

    result, raw_output, llm_trace = service.invoke_generation(
        prompt=prompt_messages,
        output_validator=SuggestionOutputValidator(),
        validator_context=schema_fields,
        llm_context={},
        structured_schema=build_suggestion_structured_schema(schema_fields),
    )

    assert result == {
        "annotationData": {
            "tags": ["a", "b"],
            "comment": "ready",
        }
    }
    assert json.loads(raw_output)["annotationData"]["tags"] == ["a", "b"]
    assert llm_trace["attempts"] == 1
    assert llm_trace["success"] is True
    assert llm_trace["localRepairs"] == [
        {
            "attempt": 1,
            "actions": [
                "tags: removed 1 duplicate multi-choice values",
            ],
        }
    ]
    assert len(FakeChatOpenAI.prompts) == 1


# 验证重试提示词包含上次校验错误信息
def test_llm_retry_prompt_includes_last_validation_error(monkeypatch):
    install_fake_chat(
        monkeypatch,
        [
            {
                "scores": [
                    {"dimension": "relevance", "score": 90, "reason": "Answer stays on topic."},
                ],
                "summary": "Missing one dimension.",
                "confidence": 0.6,
            },
            valid_output(),
        ],
    )
    service = build_service()

    model_result = service.invoke_audit("Original prompt", DIMENSIONS, {})

    assert model_result is not None
    retry_prompt = FakeChatOpenAI.prompts[-1]
    assert "上一次模型输出未通过校验。" in retry_prompt
    assert "校验错误：missing score for dimension: accuracy" in retry_prompt
    assert "请根据上次错误修正输出。" in retry_prompt


# 验证重试仅追加到用户消息，不影响系统消息
def test_llm_retry_only_appends_to_user_message(monkeypatch):
    install_fake_chat(
        monkeypatch,
        [
            {
                "scores": [
                    {"dimension": "relevance", "score": 90, "reason": "Answer stays on topic."},
                ],
                "summary": "Missing one dimension.",
                "confidence": 0.6,
            },
            valid_output(),
        ],
    )
    service = build_service()
    prompt_messages = PromptMessages(
        system_prompt="Stable system",
        user_prompt="Dynamic user context",
    )

    model_result = service.invoke_audit(
        prompt_messages.to_text(),
        DIMENSIONS,
        {},
        prompt_messages=prompt_messages,
    )

    assert model_result is not None
    retry_prompt = FakeChatOpenAI.prompts[-1]
    assert retry_prompt[0] == ("system", "Stable system")
    assert retry_prompt[1][0] == "human"
    assert retry_prompt[1][1].startswith("Dynamic user context")
    assert "上一次模型输出未通过校验。" in retry_prompt[1][1]
    assert "missing score for dimension: accuracy" in retry_prompt[1][1]


def test_llm_multimodal_prompt_falls_back_to_text_only_when_provider_rejects_image_url(monkeypatch):
    install_fake_chat(
        monkeypatch,
        [
            Exception(
                "Error code: 400 - Failed to deserialize the JSON body into the target type: "
                "messages[1]: unknown variant `image_url`, expected `text`"
            ),
            valid_output(),
        ],
    )
    service = build_service()
    prompt_messages = PromptMessages(
        system_prompt="System instructions",
        user_prompt="User payload",
        user_parts=(PromptContentPart.image_url_part("https://example.com/demo.jpg"),),
    )

    model_result = service.invoke_audit(
        prompt_messages.to_text(),
        DIMENSIONS,
        {},
        prompt_messages=prompt_messages,
    )

    assert model_result is not None
    assert len(FakeChatOpenAI.prompts) == 2
    assert FakeChatOpenAI.prompts[0] == [
        {"role": "system", "content": "System instructions"},
        {
            "role": "user",
            "content": [
                {"type": "text", "text": "User payload"},
                {"type": "image_url", "image_url": {"url": "https://example.com/demo.jpg"}},
            ],
        },
    ]
    assert FakeChatOpenAI.prompts[1] == [
        ("system", "System instructions"),
        ("human", "User payload\n\n附件证据：\n[image] https://example.com/demo.jpg"),
    ]


# 验证超过最大重试次数后返回None
def test_llm_structured_output_failure_after_max_retries(monkeypatch):
    install_fake_chat(
        monkeypatch,
        ["bad-1", "bad-fb-1", "bad-2", "bad-fb-2", "bad-3", "bad-fb-3"],
    )
    service = build_service()

    model_result = service.invoke_audit("Original prompt", DIMENSIONS, {})

    assert model_result is None


# 验证建议模式是动态生成且严格的
def test_suggestion_schema_is_dynamic_and_strict():
    schema = build_suggestion_structured_schema(
        [
            SchemaField(field="quality", label="Quality", type="radio", required=True, options=["1", "2"]),
            SchemaField(field="issues", label="Issues", type="multiSelect", required=True, options=["a", "b"]),
            SchemaField(field="comment", label="Comment", type="textarea", required=True, config={"maxLength": 50}),
            SchemaField(field="display_only", label="Display Only", type="display"),
        ]
    )

    annotation_data = schema["properties"]["annotationData"]

    assert schema["title"] == "SuggestionAnnotationOutput"
    assert annotation_data["additionalProperties"] is False
    assert set(annotation_data["required"]) == {"quality", "issues", "comment", "display_only"}
    assert annotation_data["properties"]["display_only"]["type"] == "string"
    assert annotation_data["properties"]["quality"]["enum"] == ["1", "2"]
    assert annotation_data["properties"]["issues"]["items"]["enum"] == ["a", "b"]


# 验证审核模式有顶层标题
def test_audit_schema_has_top_level_title():
    schema = AUDIT_RESULT_SCHEMA.model_json_schema()

    assert schema["title"] == "AuditStructuredOutput"


# 验证客户端将Pydantic输出转换为字典
def test_client_coerces_pydantic_output_to_dict():
    parsed = openai_client_module.OpenAICompatibleLLMClient._coerce_json_object(
        AUDIT_RESULT_SCHEMA(
            scores=[
                {"dimension": "relevance", "score": 90, "reason": "ok"},
                {"dimension": "accuracy", "score": 85, "reason": "ok"},
            ],
            summary="ok",
            confidence=0.9,
        )
    )

    assert parsed["summary"] == "ok"
    assert isinstance(parsed, dict)
