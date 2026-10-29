"""测试标注模式归一化与校验规则。"""

from annotation_agent.contracts.audit_request import AuditRequest
from annotation_agent.domain.audit.rule_validator import AnnotationRuleValidator
from annotation_agent.domain.shared.task_schema import AnnotationSchemaParser


# 构造测试用审核载荷
def build_payload(component: dict, annotation_result: dict) -> dict:
    field_name = component["field"]
    return {
        "taskItemId": "case-1",
        "taskTitle": "audit test",
        "taskDescription": "audit test description",
        "rawData": {"prompt": "demo"},
        "annotationSchema": {"components": [component]},
        "annotationResult": annotation_result,
        "reviewConfig": {
            "reviewPrompt": "validate the annotation result",
            "dimensions": [{"field": field_name, "weight": 1.0, "passScore": 80}],
            "passThreshold": 85,
            "reviewThreshold": 70,
        },
    }


# 验证tagSelect类型在multiple=True时归一化为multiSelect
def test_audit_request_normalizes_tag_select_multiple_to_multi_select():
    payload = build_payload(
        {
            "field": "dimension_tags",
            "label": "Dimension Tags",
            "type": "tagSelect",
            "multiple": True,
            "options": ["safety", "policy"],
        },
        {"dimension_tags": ["safety"]},
    )

    request = AuditRequest.from_payload("task-1", payload)

    component = request.annotation_schema["components"][0]
    assert component["type"] == "multiSelect"
    assert component["multiple"] is True
    assert component["config"]["multiple"] is True


# 验证模式归一化后，校验器接受多选列表值
def test_validator_accepts_multi_select_list_after_schema_normalization():
    payload = build_payload(
        {
            "field": "dimension_tags",
            "label": "Dimension Tags",
            "type": "select",
            "config": {"multiple": True},
            "options": ["safety", "policy"],
        },
        {"dimension_tags": ["safety", "policy"]},
    )
    request = AuditRequest.from_payload("task-1", payload)

    schema_fields = AnnotationSchemaParser().parse(request.annotation_schema)
    issues = AnnotationRuleValidator().validate(schema_fields, request.annotation_result)

    assert schema_fields[0].type == "multiSelect"
    assert issues == []


# 验证单选字段传入列表时校验器报告错误而非崩溃
def test_validator_reports_single_choice_list_instead_of_crashing():
    payload = build_payload(
        {
            "field": "verdict",
            "label": "Verdict",
            "type": "radio",
            "options": ["pass", "reject"],
        },
        {"verdict": ["pass"]},
    )
    request = AuditRequest.from_payload("task-1", payload)

    schema_fields = AnnotationSchemaParser().parse(request.annotation_schema)
    issues = AnnotationRuleValidator().validate(schema_fields, request.annotation_result)

    assert len(issues) == 1
    assert issues[0]["field"] == "verdict"
    assert "expects a single value" in issues[0]["message"]


# 验证多选字段传入嵌套列表时校验器报告错误而非崩溃
def test_validator_reports_nested_multi_select_values_instead_of_crashing():
    payload = build_payload(
        {
            "field": "dimension_tags",
            "label": "Dimension Tags",
            "type": "multiSelect",
            "options": ["safety", "policy"],
        },
        {"dimension_tags": [["safety"]]},
    )
    request = AuditRequest.from_payload("task-1", payload)

    schema_fields = AnnotationSchemaParser().parse(request.annotation_schema)
    issues = AnnotationRuleValidator().validate(schema_fields, request.annotation_result)

    assert len(issues) == 1
    assert issues[0]["field"] == "dimension_tags"
    assert "contains invalid options" in issues[0]["message"]
