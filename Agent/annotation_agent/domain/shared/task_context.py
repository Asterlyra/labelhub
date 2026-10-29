"""审核与建议流程共享的稳定任务上下文。"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any

from annotation_agent.domain.shared.task_schema import SchemaField


@dataclass
class TaskContext:
    """审核/建议流程共享的任务上下文，封装任务元数据与模式信息。"""
    task_id: str  # 任务 ID
    project_id: str  # 项目 ID
    task_item_id: str  # 任务子项 ID
    task_title: str  # 任务标题
    task_description: str  # 任务描述
    raw_data: dict[str, Any] | str  # 原始待标注数据
    schema_fields: list[SchemaField]  # 标注模式字段列表
    input_hash: str  # 输入内容哈希，用于幂等判断
    idempotency_key: str  # 幂等键

    def to_llm_context(self, extra: dict[str, Any] | None = None) -> dict[str, Any]:
        """构建供 LLM 使用的上下文字典。"""
        context = {
            "rawData": self.raw_data,
            "annotationSchema": [field.to_dict() for field in self.schema_fields],
        }
        if extra:
            context.update(extra)
        return context
