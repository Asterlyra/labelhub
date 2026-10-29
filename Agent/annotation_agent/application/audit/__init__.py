"""审核应用服务模块。

本模块实现AI预审的完整业务流程，从接收审核请求到输出审核结果。

核心服务：
- AuditExecutionService: 单次审核执行服务
  - 接收task_id和payload，构建AuditRequest
  - 通过AuditContextBuilder组装审核上下文（校验、提示词、证据）
  - 检查幂等缓存，命中则直接返回已有结果
  - 调用LLM网关执行审核，获取模型输出
  - 通过AuditDecisionPolicy决策，生成最终审核结果
  - 持久化结果到仓储

- AuditJobService: 审核任务生命周期管理
  - 处理后端RabbitMQ事件，管理任务状态流转（pending -> processing -> completed/failed）
  - 实现重试控制：超过最大重试次数标记为max_retries_exceeded
  - 保存执行追踪（入站事件、LLM提示词）

- AuditQueryService: 审核结果查询（只读）

辅助组件：
- AuditContextBuilder: 审核上下文组装器
  - 解析标注模式、执行确定性校验、渲染提示词模板、构建证据包
- AuditPromptBuilder: 提示词渲染器
  - 将审核配置、原始数据、标注结果等填充到提示词模板中
"""
