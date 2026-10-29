"""LLM 共享基础设施。"""

from annotation_agent.infrastructure.llm.shared.openai_compatible_client import (
    OpenAICompatibleLLMClient,
    reset_structured_output_support_cache,
)
from annotation_agent.infrastructure.llm.shared.output_validation import OutputValidationError
from annotation_agent.infrastructure.llm.shared.prompt_messages import PromptContentPart, PromptMessages

__all__ = [
    "OpenAICompatibleLLMClient",
    "OutputValidationError",
    "PromptContentPart",
    "PromptMessages",
    "reset_structured_output_support_cache",
]
