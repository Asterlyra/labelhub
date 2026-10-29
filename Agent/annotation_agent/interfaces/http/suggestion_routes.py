"""建议工作流的HTTP路由。"""

from __future__ import annotations

from flask import Blueprint, current_app, jsonify, request

from annotation_agent.contracts.api_response import error_response, success_response


suggestion_blueprint = Blueprint("suggestion", __name__)


def _container():
    """获取当前Flask应用上下文中的服务容器。"""
    return current_app.extensions["service_container"]


@suggestion_blueprint.post("/api/ai-annotation/suggestions")
def submit_suggestion():
    """提交标注建议请求。"""
    payload = request.get_json(silent=True) or {}
    try:
        result = _container().suggestion_command_service.submit(payload)
    except ValueError as exc:
        return jsonify(
            error_response(str(exc), code="VALIDATION_ERROR", data={"taskId": str(payload.get("taskId") or "")})
        ), 400
    return jsonify(success_response(result))


@suggestion_blueprint.post("/api/ai-annotation/suggestions/retry")
def retry_suggestion():
    """重试标注建议请求。"""
    payload = request.get_json(silent=True) or {}
    try:
        result = _container().suggestion_command_service.retry(payload)
    except ValueError as exc:
        return jsonify(
            error_response(str(exc), code="VALIDATION_ERROR", data={"taskId": str(payload.get("taskId") or "")})
        ), 400
    return jsonify(success_response(result))


@suggestion_blueprint.get("/api/ai-annotation/task-items/<task_item_id>/suggestions/<suggestion_id>")
def get_suggestion(task_item_id: str, suggestion_id: str):
    """查询指定建议的执行结果。"""
    result = _container().suggestion_query_service.get_result(task_item_id, suggestion_id)
    if not result:
        return jsonify(
            error_response(
                "AI annotation suggestion not found",
                code="404",
                data={"taskItemId": task_item_id, "suggestionId": suggestion_id},
            )
        ), 404
    return jsonify(success_response(result))
