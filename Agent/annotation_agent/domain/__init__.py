"""领域层（Domain Layer）。

本层包含核心业务模型和领域规则，是整个Agent的业务核心，不依赖任何外部框架或基础设施。

主要子模块：
- audit: 审核领域模型（审核配置、审核上下文、审核结果、决策策略、校验规则链）
- shared: 共享领域工具（枚举定义、任务上下文、标注模式解析）
- suggestion: 建议领域模型（当前为空，建议逻辑复用shared模块）

设计原则：
- 领域层不依赖application或infrastructure层
- 所有业务规则以纯Python类实现，便于单元测试
- 校验规则链（RuleValidationChain）采用策略模式，可灵活扩展
"""
