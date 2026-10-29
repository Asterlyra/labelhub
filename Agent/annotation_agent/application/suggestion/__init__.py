"""建议应用服务模块。

本模块实现AI标注建议的完整业务流程，从接收建议请求到输出标注建议。

核心服务：
- SuggestionCommandService: 建议提交与分发服务
  - 接收请求后创建pending状态的任务记录
  - 支持同步/异步两种执行模式（默认异步，使用线程池）
  - 异步模式下通过ThreadPoolExecutor分发任务（默认4个工作线程）

- SuggestionExecutionService: 建议执行服务
  - 从仓储加载任务记录，构建SuggestionRequest
  - 通过SuggestionPromptBuilder构建system/user提示词
  - 调用LLM网关生成标注建议（支持结构化输出模式）
  - 校验LLM输出格式，持久化结果
  - 失败时记录错误信息和原始模型输出

- SuggestionQueryService: 建议结果查询（只读）

辅助组件：
- SuggestionPromptBuilder: 提示词构建器
  - 加载system/user模板，将任务上下文填充到用户模板中
- SuggestionResultFactory: 结果工厂
  - 生成suggestion_id（sug-前缀+12位UUID）
  - 构建提交结果和公开结果两种视图
"""
