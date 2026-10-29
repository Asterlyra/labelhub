"""外部请求与响应契约模块。

本模块定义Agent服务与外部系统交互的数据契约，包括请求验证和响应格式。

核心契约：
- AuditRequest: 审核请求契约
  - 从后端payload构造，验证rawData、annotationSchema、annotationResult、reviewConfig必填
  - 对annotationSchema执行归一化（tagSelect->multiSelect等类型转换）
  - 包含task_id、task_item_id、task_title、task_description等元数据

- SuggestionRequest: 建议请求契约
  - 从后端payload构造，验证taskId、taskItemId、materials、rawData必填
  - 将materials转换为annotationSchema（通过material_schema_adapter）

- api_response: API响应辅助函数
  - success_response(): 构造成功响应（code=200, message="success"）
  - error_response(): 构造错误响应（支持自定义code和message）

所有契约采用dataclass实现，支持序列化为字典/JSON。
"""
