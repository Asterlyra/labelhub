"""测试审核结果查询接口。"""


def test_audit_query_route_keeps_legacy_fields_and_adds_media_result(client, audit_repo):
    audit_repo.save_result(
        {
            "taskItemId": "item-1",
            "taskId": "task-1",
            "status": "completed",
            "auditStatus": "pass",
            "scores": [{"dimension": "accuracy_score", "score": 92, "reason": "ok"}],
            "weightedScore": 92.0,
            "summary": "Audit passed.",
            "confidence": 0.9,
            "lastError": "",
            "mediaResult": {
                "detected": True,
                "degraded": False,
                "assets": [
                    {
                        "assetId": "rawData.media_url#0",
                        "kind": "image",
                        "sourcePath": "rawData.media_url",
                        "origin": "raw",
                        "locator": "https://example.com/cover.jpg",
                        "mimeHint": "image/jpeg",
                    }
                ],
                "evidenceUsed": [],
                "mediaWarnings": [],
            },
        }
    )

    response = client.get("/api/ai-audit/tasks/item-1/result")

    assert response.status_code == 200
    payload = response.get_json()["data"]
    assert payload["taskItemId"] == "item-1"
    assert payload["auditStatus"] == "pass"
    assert payload["summary"] == "Audit passed."
    assert payload["mediaResult"]["detected"] is True


def test_audit_query_route_lists_results_by_task_id(client, audit_repo):
    audit_repo.save_result(
        {
            "taskItemId": "item-2",
            "taskId": "task-1",
            "status": "pending",
            "auditStatus": "",
        }
    )
    audit_repo.save_result(
        {
            "taskItemId": "item-1",
            "taskId": "task-1",
            "status": "completed",
            "auditStatus": "pass",
        }
    )
    audit_repo.save_result(
        {
            "taskItemId": "item-3",
            "taskId": "task-2",
            "status": "failed",
            "auditStatus": "",
        }
    )

    response = client.get("/api/ai-audit/tasks/task-1/results")

    assert response.status_code == 200
    payload = response.get_json()["data"]
    assert payload == [
        {
            "taskItemId": "item-1",
            "taskId": "task-1",
            "status": "completed",
            "auditStatus": "pass",
        },
        {
            "taskItemId": "item-2",
            "taskId": "task-1",
            "status": "pending",
            "auditStatus": "",
        },
    ]


def test_audit_query_route_lists_empty_results_for_unknown_task_id(client):
    response = client.get("/api/ai-audit/tasks/task-missing/results")

    assert response.status_code == 200
    assert response.get_json()["data"] == []
