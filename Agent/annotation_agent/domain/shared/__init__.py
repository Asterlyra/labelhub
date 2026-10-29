"""共享领域工具模块。

本模块提供审核和建议流程共享的领域基础组件：

- enums: 共享枚举定义
  - AuditStatus: 审核状态（pass/manual_review/reject）
  - TaskStatus: 任务状态（pending/processing/completed/failed/max_retries_exceeded）

- task_schema: 标注模式解析工具
  - SchemaField: 标注字段定义（字段名、类型、选项、是否必填等）
  - AnnotationSchemaParser: 模式解析器，将后端JSON模式转为SchemaField列表
  - normalize_annotation_schema: 模式归一化函数，处理tagSelect->multiSelect等类型转换

- task_context: 任务上下文
  - TaskContext: 审核与建议流程共享的稳定任务上下文，包含任务元数据、模式字段、幂等键等
"""
