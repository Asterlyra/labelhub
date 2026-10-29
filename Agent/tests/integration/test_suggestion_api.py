"""测试建议API接口与输出校验。"""

import json

import pytest

from annotation_agent.domain.shared.task_schema import AnnotationSchemaParser
from annotation_agent.infrastructure.llm.shared.output_validation import OutputValidationError
from annotation_agent.infrastructure.llm.suggestion.output_validator import SuggestionOutputValidator


# 从响应中提取data字段
def response_data(response):
    body = response.get_json()
    return body["data"]


# 从响应中提取错误信息
def response_error(response):
    return response.get_json()


# 构造建议请求测试载荷
def suggestion_payload(task_id):
    return {
        "taskId": task_id,
        "taskItemId": "item-1001",
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
            {
                "field": "一句话总评",
                "label": "输入框",
                "required": 1,
                "config": {"maxLength": 60},
            },
            {
                "field": "详细评语",
                "label": "详情页内容",
                "required": 0,
                "config": {"maxLength": 2000},
            },
        ],
        "rawData": {
            "prompt": "Summarize the following meeting notes.",
            "model_answer": "The meeting confirmed the Q3 release plan.",
            "reference": "Cover all key decisions and deadlines.",
        },
    }


# 验证提交建议返回pending状态的任务ID
def test_submit_suggestion_returns_pending_job_id(client, run_id):
    task_id = f"suggestion-task-{run_id}"

    response = client.post(
        "/api/ai-annotation/suggestions",
        json=suggestion_payload(task_id),
    )

    assert response.status_code == 200
    result = response_data(response)
    assert result["taskId"] == task_id
    assert result["taskItemId"] == "item-1001"
    assert result["status"] == "pending"
    assert result["suggestionId"].startswith("sug-")


# 验证查询建议返回已完成的标注数据
def test_get_suggestion_returns_completed_annotation_data(client, run_id):
    task_id = f"suggestion-query-{run_id}"
    created = response_data(
        client.post(
            "/api/ai-annotation/suggestions",
            json=suggestion_payload(task_id),
        )
    )

    response = client.get(
        f"/api/ai-annotation/task-items/item-1001/suggestions/{created['suggestionId']}"
    )

    assert response.status_code == 200
    loaded = response_data(response)
    assert loaded["suggestionId"] == created["suggestionId"]
    assert loaded["status"] == "completed"
    assert loaded["annotationData"]["原始题面"] == "原始题面的建议值"
    assert loaded["annotationData"]["相关性评分"] == "1"
    assert loaded["annotationData"]["问题类型标签"] == ["信息缺失"]
    assert loaded["annotationData"]["一句话总评"]


def test_suggestion_job_persists_final_pipeline_prompt(client, suggestion_repo, run_id):
    task_id = f"suggestion-prompt-{run_id}"
    created = response_data(
        client.post(
            "/api/ai-annotation/suggestions",
            json=suggestion_payload(task_id),
        )
    )

    saved_job = suggestion_repo.get_job(created["suggestionId"])

    assert saved_job is not None
    assert saved_job["status"] == "completed"
    assert "requiredFields" in saved_job["llmPrompt"]
    assert "constrainedFields" in saved_job["llmPrompt"]


def test_suggestion_job_persists_final_annotation_data_as_raw_model_output(client, suggestion_repo, run_id):
    task_id = f"suggestion-raw-output-{run_id}"
    created = response_data(
        client.post(
            "/api/ai-annotation/suggestions",
            json=suggestion_payload(task_id),
        )
    )

    saved_job = suggestion_repo.get_job(created["suggestionId"])

    assert saved_job is not None
    assert saved_job["status"] == "completed"
    assert json.loads(saved_job["rawModelOutput"]) == {
        "annotationData": saved_job["annotationData"],
    }


def test_get_suggestion_requires_matching_task_item_id(client, run_id):
    task_id = f"suggestion-query-mismatch-{run_id}"
    created = response_data(
        client.post(
            "/api/ai-annotation/suggestions",
            json=suggestion_payload(task_id),
        )
    )

    response = client.get(
        f"/api/ai-annotation/task-items/wrong-item/suggestions/{created['suggestionId']}"
    )

    assert response.status_code == 404
    body = response_error(response)
    assert body["data"] == {
        "taskItemId": "wrong-item",
        "suggestionId": created["suggestionId"],
    }


# 验证相同任务项和相同内容复用已完成建议
def test_retry_reuses_completed_suggestion_for_same_task_item_hash(client, run_id):
    task_id = f"suggestion-retry-{run_id}"
    payload = suggestion_payload(task_id)

    first = response_data(client.post("/api/ai-annotation/suggestions", json=payload))
    second = response_data(client.post("/api/ai-annotation/suggestions/retry", json=payload))

    assert first["suggestionId"] == second["suggestionId"]
    assert second["status"] == "completed"


def test_retry_creates_new_suggestion_when_same_task_item_content_changes(client, run_id):
    task_id = f"suggestion-retry-change-{run_id}"
    payload = suggestion_payload(task_id)

    first = response_data(client.post("/api/ai-annotation/suggestions", json=payload))
    payload["rawData"]["reference"] = "Cover all key decisions, owners, and delivery dates."
    second = response_data(client.post("/api/ai-annotation/suggestions/retry", json=payload))

    assert first["suggestionId"] != second["suggestionId"]
    assert second["status"] == "pending"


def test_submit_reuses_completed_suggestion_for_different_task_item_same_hash(client, run_id):
    first_payload = suggestion_payload(f"suggestion-cross-item-{run_id}-1")
    second_payload = suggestion_payload(f"suggestion-cross-item-{run_id}-2")
    second_payload["taskItemId"] = "item-2002"

    first = response_data(client.post("/api/ai-annotation/suggestions", json=first_payload))
    second = response_data(client.post("/api/ai-annotation/suggestions", json=second_payload))

    assert first["suggestionId"] != second["suggestionId"]
    assert second["taskItemId"] == "item-2002"
    assert second["status"] == "completed"

    loaded = response_data(
        client.get(f"/api/ai-annotation/task-items/item-2002/suggestions/{second['suggestionId']}")
    )
    assert loaded["status"] == "completed"
    assert loaded["annotationData"]["相关性评分"] == "1"


# 验证建议请求要求body中提供taskId
def test_suggestion_request_requires_task_id_in_body(client, run_id):
    task_id = f"suggestion-invalid-{run_id}"
    payload = suggestion_payload(task_id)
    payload.pop("taskId")

    response = client.post(
        "/api/ai-annotation/suggestions",
        json=payload,
    )

    assert response.status_code == 400
    assert response_error(response)["code"] == "VALIDATION_ERROR"


# 验证建议输出校验器拒绝模式外的选项值
def test_suggestion_output_validator_rejects_option_outside_schema():
    fields = AnnotationSchemaParser().parse(
        {
            "components": [
                {"field": "quality_level", "label": "Quality Level", "type": "radio", "options": ["good"]},
                {"field": "comment", "label": "Comment", "type": "textarea", "required": False},
            ]
        }
    )

    with pytest.raises(OutputValidationError, match="must be one of field options"):
        SuggestionOutputValidator().parse_and_validate(
            json.dumps(
                {
                    "annotationData": {
                        "quality_level": "bad",
                        "comment": "",
                    }
                },
                ensure_ascii=False,
            ),
            fields,
        )


def test_suggestion_output_validator_rejects_all_empty_optional_values():
    fields = AnnotationSchemaParser().parse(
        {
            "components": [
                {"field": "detailHtml", "label": "Detail", "type": "textarea", "required": False},
                {
                    "field": "sellingPoints",
                    "label": "Selling Points",
                    "type": "select",
                    "multiple": True,
                    "options": ["发货快", "价格低"],
                    "required": False,
                },
            ]
        }
    )

    with pytest.raises(OutputValidationError, match="at least one meaningful value"):
        SuggestionOutputValidator().parse_and_validate(
            json.dumps(
                {
                    "annotationData": {
                        "detailHtml": "",
                        "sellingPoints": [],
                    }
                },
                ensure_ascii=False,
            ),
            fields,
        )
