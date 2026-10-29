"""审核领域模块。

本模块定义AI预审的核心领域模型和业务规则，包括：

核心模型：
- AuditConfig: 审核配置，包含审核维度（dimensions）、通过阈值（passThreshold）、复审阈值（reviewThreshold）
- AuditDimension: 审核维度定义，包含维度字段名、权重、及格分
- AuditContext: 审核执行上下文，聚合请求、配置、校验结果、证据包和提示词
- AuditResult: 审核结果实体，包含状态、评分、置信度、加权得分等

业务规则：
- AuditDecisionPolicy: 决策策略，根据加权得分和校验问题确定 pass/manual_review/reject
- AnnotationRuleValidator: 确定性校验器，在调用LLM前对标注结果做格式校验
- RuleValidationChain: 校验规则链，按序执行必填、单选、多选、JSON、评分等校验规则
- AuditResultFactory: 审核结果工厂，构建成功/失败的审核结果载荷
"""
