"""LLM 基础设施适配模块。

目录按业务场景拆分：
- `shared`：审核与建议共用的 Prompt、provider client、共享异常
- `audit`：审核链路专用的 schema、validator、mock、本地修复
- `suggestion`：建议链路专用的 schema、validator、本地修复

`LLMService` 仍然作为统一门面，对外实现 `LLMGateway`。
"""

from annotation_agent.infrastructure.llm.llm_service import LLMService

__all__ = ["LLMService"]
