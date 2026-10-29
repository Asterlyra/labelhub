"""基础设施层。

本层负责对外部依赖做适配，并通过协议接口与 application 层解耦。

主要子模块：
- `llm`：LLM 调用适配
  - `shared`：审核与建议共用的 prompt、provider client、共享异常
  - `audit`：审核链路专用的 schema、validator、mock 与本地修复
  - `suggestion`：建议链路专用的 schema、validator 与本地修复
- `persistence`：持久化适配
- `integrations`：外部系统集成
- `prompts`：提示词模板加载与管理
"""
