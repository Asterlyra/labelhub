"""少样本检索技能：检索与当前任务相似的历史标注示例。"""

from __future__ import annotations

from annotation_agent.contracts.suggestion_request import SuggestionRequest
from annotation_agent.domain.shared.task_context import TaskContext
from annotation_agent.infrastructure.tools.suggestion_tools import SuggestionTools


class FewShotRetrievalSkill:
    """少样本检索技能。

    流水线的前置阶段，根据当前任务的标题、描述、Schema 字段和原始数据，
    从历史标注记录中检索相似的示例，用于后续草稿生成阶段增强提示词。
    """

    def __init__(self, tools: SuggestionTools) -> None:
        self.tools = tools

    def run(self, request: SuggestionRequest, context: TaskContext) -> list[dict]:
        """执行相似示例检索。

        Args:
            request: 标注建议请求，包含任务标题、描述和原始数据。
            context: 任务上下文，包含 Schema 字段定义。

        Returns:
            与当前任务相似的历史标注示例列表。
        """
        return self.tools.retrieve_similar_examples.invoke(
            {
                "task_title": request.task_title,
                "task_description": request.task_description,
                "schema_fields": [field.to_dict() for field in context.schema_fields],
                "raw_data": request.raw_data,
            }
        )
