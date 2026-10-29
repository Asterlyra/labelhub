"""审核结果查询的HTTP路由。"""

from __future__ import annotations

from flask import Blueprint, current_app, jsonify

from annotation_agent.contracts.api_response import error_response, success_response


audit_query_blueprint = Blueprint("audit_query", __name__)


def _container():
    """获取当前Flask应用上下文中的服务容器。"""
    return current_app.extensions["service_container"]


@audit_query_blueprint.get("/api/ai-audit/tasks/<task_item_id>/result")
def get_audit_result(task_item_id: str):
    """根据任务项ID查询AI审核结果。"""
    result = _container().audit_query_service.get_result(task_item_id)
    if not result:
        return jsonify(error_response("Audit task not found", code="404", data={"taskItemId": task_item_id})), 404

    return jsonify(
        success_response(
            {
                "taskItemId": result.get("taskItemId", ""),
                "taskId": result.get("taskId", ""),
                "status": result.get("status", ""),
                "auditStatus": result.get("auditStatus", ""),
                "scores": result.get("scores", []),
                "weightedScore": result.get("weightedScore", 0),
                "summary": result.get("summary", ""),
                "confidence": result.get("confidence", 0),
                "lastError": result.get("lastError", ""),
                "mediaResult": result.get("mediaResult"),
            }
        )
    )


@audit_query_blueprint.get("/api/ai-audit/tasks/<task_id>/results")
def list_audit_results(task_id: str):
    results = _container().audit_query_service.list_results_by_task_id(task_id)
    return jsonify(
        success_response(
            [
                {
                    "taskItemId": result.get("taskItemId", ""),
                    "taskId": result.get("taskId", ""),
                    "status": result.get("status", ""),
                    "auditStatus": result.get("auditStatus", ""),
                }
                for result in results
            ]
        )
    )
