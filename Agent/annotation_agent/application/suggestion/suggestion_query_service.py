"""建议查询服务。"""

from __future__ import annotations

from annotation_agent.application.ports import SuggestionJobRepository
from annotation_agent.application.suggestion.suggestion_result_factory import SuggestionResultFactory


# 建议查询服务：提供建议结果的只读查询能力
class SuggestionQueryService:
    def __init__(
        self,
        suggestion_repository: SuggestionJobRepository,
        result_factory: SuggestionResultFactory,
    ) -> None:
        self.suggestion_repository = suggestion_repository
        self.result_factory = result_factory

    # 根据 task_item_id + suggestion_id 获取建议结果（公开视图，包含标注数据与错误信息）
    def get_result(self, task_item_id: str, suggestion_id: str) -> dict | None:
        suggestion_job = self.suggestion_repository.get_job_for_task_item(suggestion_id, task_item_id)
        if not suggestion_job:
            return None
        return self.result_factory.build_public_result(suggestion_job)
