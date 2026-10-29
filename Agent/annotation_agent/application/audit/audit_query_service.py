"""审核结果只读查询应用服务。"""

from __future__ import annotations

from annotation_agent.application.ports import AuditResultRepository


# 审核查询服务：提供审核结果的只读查询能力
class AuditQueryService:
    def __init__(self, audit_repository: AuditResultRepository) -> None:
        self.audit_repository = audit_repository

    # 根据 task_item_id 获取审核结果
    def get_result(self, task_item_id: str) -> dict | None:
        return self.audit_repository.get_result(task_item_id)

    def list_results_by_task_id(self, task_id: str) -> list[dict]:
        return self.audit_repository.list_results_by_task_id(task_id)
