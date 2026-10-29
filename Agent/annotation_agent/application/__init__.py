"""应用层（Application Layer）。

本层编排领域模型和基础设施服务，实现核心业务用例。不包含业务规则本身（规则在domain层），而是协调各组件完成端到端的业务流程。

主要子模块：
- audit: 审核应用服务
  - AuditExecutionService: 单次审核执行（构建上下文 -> 调用LLM -> 决策 -> 持久化）
  - AuditJobService: 审核任务生命周期管理（状态流转、重试控制、事件处理）
  - AuditQueryService: 审核结果只读查询
  - AuditContextBuilder: 审核上下文组装（校验、提示词渲染、证据包构建）
  - AuditPromptBuilder: 审核提示词模板渲染

- suggestion: 建议应用服务
  - SuggestionCommandService: 建议提交与异步分发
  - SuggestionExecutionService: 建议执行（构建system/user提示词 -> 调用LLM -> 校验输出 -> 持久化）
  - SuggestionQueryService: 建议结果查询
  - SuggestionPromptBuilder: 建议system/user提示词构建
  - SuggestionResultFactory: 建议结果工厂

- shared: 共享应用服务
  - TaskContextBuilder: 任务上下文构建器（解析模式、计算幂等键）
  - IdempotencyService: 广幂等性服务（请求哈希、幂等键生成）

依赖关系：
- application层依赖domain层和contracts层
- 通过ports.py定义的Protocol接口依赖infrastructure层（依赖倒置）
"""
