"""共享应用服务模块。

本模块提供审核和建议流程共享的应用层基础设施：

- TaskContextBuilder: 任务上下文构建器
  - 解析后端传入的annotationSchema为SchemaField列表
  - 计算请求载荷的SHA256哈希作为幂等键的一部分
  - 组装TaskContext，包含任务元数据、模式字段、输入哈希、幂等键

- IdempotencyService: 幂等性服务
  - build_payload_hash(): 将请求载荷序列化为JSON后计算SHA256哈希
  - build_key(): 组合task_id和input_hash生成幂等键

这些组件被AuditContextBuilder和SuggestionCommandService共同使用，避免重复实现。
"""
