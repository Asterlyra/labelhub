"""HTTP接口适配模块。

本模块提供Agent服务的RESTful HTTP接口，基于Flask Blueprint实现。

路由蓝图：
- suggestion_blueprint: 建议工作流路由
  - POST /api/ai-annotation/suggestions: 提交标注建议请求
  - POST /api/ai-annotation/suggestions/retry: 重试建议生成
  - GET /api/ai-annotation/task-items/<task_item_id>/suggestions/<suggestion_id>: 查询建议结果

- audit_query_blueprint: 审核结果查询路由
  - GET /api/ai-audit/tasks/<task_item_id>/result: 查询审核结果

通用路由（在bootstrap.py中注册）：
  - GET /health: 健康检查（返回服务状态）
  - GET /ready: 就绪检查（检查MySQL、LLM、RabbitMQ连接状态）

所有接口返回统一的JSON响应格式：{code, message, data}
"""
