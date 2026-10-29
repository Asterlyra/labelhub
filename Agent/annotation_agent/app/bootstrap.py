"""Flask应用引导。"""

from __future__ import annotations

import logging
import os
import hmac
import sys

from flask import Flask, request, jsonify

from annotation_agent.app.container import ServiceContainer
from annotation_agent.contracts.api_response import success_response
from annotation_agent.interfaces.http.audit_query_routes import audit_query_blueprint
from annotation_agent.interfaces.http.suggestion_routes import suggestion_blueprint


def create_app(container: ServiceContainer | None = None) -> Flask:
    """创建并配置Flask应用实例。"""
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s [%(threadName)s] %(levelname)s %(name)s - %(message)s",
        datefmt="%Y-%m-%d %H:%M:%S",
        stream=sys.stdout,
    )

    logging.getLogger("pika").setLevel(logging.WARNING)
    logging.getLogger("werkzeug").setLevel(logging.WARNING)

    app = Flask(__name__)
    app.json.ensure_ascii = False  # 允许JSON响应中直接输出中文

    app.config["MAX_CONTENT_LENGTH"] = 16 * 1024 * 1024

    @app.before_request
    def authorize_internal_api():
        if not request.path.startswith("/api/"):
            return None
        expected = os.environ.get("LABELHUB_AGENT_TOKEN", "")
        provided = request.headers.get("X-LabelHub-Agent-Token", "")
        if len(expected) < 32:
            return jsonify({"error": "Agent service token is not configured"}), 503
        if not hmac.compare_digest(expected, provided):
            return jsonify({"error": "Unauthorized"}), 401
        return None

    # 初始化服务容器并注册到Flask扩展中
    service_container = container or ServiceContainer()
    app.extensions["service_container"] = service_container
    app.config["rabbitmq_audit_consumer"] = None

    # 注册蓝图路由
    app.register_blueprint(suggestion_blueprint)
    app.register_blueprint(audit_query_blueprint)

    @app.get("/health")
    def health():
        """健康检查端点。"""
        return success_response({"status": "ok", "service": "labelhub-agent"})

    @app.get("/ready")
    def ready():
        """就绪检查端点，返回各依赖的可用状态。"""
        return success_response(service_container.readiness_report())

    return app
