"""建议流水线各阶段共享的数据类型。"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any


@dataclass
class SuggestionStageResult:
    """建议流水线单个阶段的执行结果。"""

    stage_name: str
    """阶段名称，如 draft_generation、schema_repair。"""

    annotation_data: dict[str, Any] | None
    """校验通过的标注数据，失败时为 None。"""

    raw_output: str
    """LLM 返回的原始输出文本。"""

    llm_trace: dict[str, Any]
    """LLM 调用追踪信息。"""

    prompt_text: str
    """该阶段发送给 LLM 的完整提示词。"""


@dataclass
class SuggestionPipelineResult:
    """建议流水线执行完成后的最终结果。"""

    annotation_data: dict[str, Any] | None
    """最终确定的标注数据，全部阶段失败时为 None。"""

    prompt_text: str
    """所有阶段聚合后的提示词。"""

    raw_output: str
    """所有阶段聚合后的原始输出。"""

    llm_trace: dict[str, Any]
    """汇总后的 LLM 调用追踪信息。"""

    stage_records: list[dict[str, Any]] = field(default_factory=list)
    """各阶段的执行记录摘要，用于调试和审计。"""
