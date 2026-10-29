"""测试审核策略与幂等性。"""

from annotation_agent.app.container import ServiceContainer


# 构造运行时审核载荷
def runtime_payload(project_id):
    return {
        "projectId": project_id,
        "taskTitle": "Answer quality audit",
        "taskDescription": "",
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
            "dimensions": [
                {"field": "format_compliance", "weight": 0.25, "passScore": 80},
                {"field": "semantic_consistency", "weight": 0.35, "passScore": 80},
                {"field": "reasoning_sufficiency", "weight": 0.25, "passScore": 75},
                {"field": "safety_compliance", "weight": 0.15, "passScore": 90},
            ],
            "passThreshold": 85,
            "reviewThreshold": 70,
        },
    }


# 验证运行时仅使用请求中的评分标准
def test_runtime_uses_request_rubric_only(settings, run_id, audit_repo):
    service = ServiceContainer(settings=settings, audit_repository=audit_repo).audit_execution_service
    project_id = f"project-policy-{run_id}"
    result = service.execute(f"policy-{run_id}", runtime_payload(project_id))

    assert {item["dimension"] for item in result["scores"]} == {
        "format_compliance",
        "semantic_consistency",
        "reasoning_sufficiency",
        "safety_compliance",
    }
    assert result["status"] == "completed"
    assert result["auditStatus"] in ("pass", "manual_review", "reject")


# 验证相同输入复用已持久化的结果（幂等性）
def test_same_input_reuses_persisted_result(settings, run_id, audit_repo):
    class FakeLLMGateway:
        def __init__(self):
            self.calls = 0

        def invoke_audit(self, _prompt, dimensions, _audit_context=None, prompt_messages=None):
            self.calls += 1
            return {
                "scores": [
                    {"dimension": dimension["field"], "score": 90, "reason": "Test pass."}
                    for dimension in dimensions
                ],
                "issues": [],
                "summary": "Audit passed.",
                "confidence": 0.9,
            }

    fake_llm_gateway = FakeLLMGateway()
    service = ServiceContainer(
        settings=settings,
        audit_repository=audit_repo,
        llm_gateway=fake_llm_gateway,
    ).audit_execution_service

    task_id = f"idem-{run_id}"
    payload = runtime_payload(f"project-idem-{run_id}")

    first = service.execute(task_id, payload)
    second = service.execute(task_id, payload)
    retry = service.execute(task_id, payload, force=True)

    assert fake_llm_gateway.calls == 2
    assert first["inputHash"] == second["inputHash"] == retry["inputHash"]
