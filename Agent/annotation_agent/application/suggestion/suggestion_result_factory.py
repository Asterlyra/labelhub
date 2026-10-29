"""建议结果工厂。"""

from __future__ import annotations

from uuid import uuid4


# 建议结果工厂：负责生成建议ID和构建不同场景下的建议结果字典
class SuggestionResultFactory:
    # 生成唯一建议ID，格式为 sug-{12位随机hex}
    def new_suggestion_id(self) -> str:
        return f"sug-{uuid4().hex[:12]}"

    # 构建提交结果（仅包含基本信息，用于响应提交请求）
    @staticmethod
    def build_submission_result(job_record: dict) -> dict:
        return {
            "suggestionId": job_record.get("suggestionId", ""),
            "taskId": job_record.get("taskId", ""),
            "taskItemId": job_record.get("taskItemId", ""),
            "status": job_record.get("status", ""),
        }

    # 构建公开结果（包含标注数据与错误信息，用于查询接口）
    @staticmethod
    def build_public_result(job_record: dict) -> dict:
        return {
            "suggestionId": job_record.get("suggestionId", ""),
            "taskId": job_record.get("taskId", ""),
            "taskItemId": job_record.get("taskItemId", ""),
            "status": job_record.get("status", ""),
            "annotationData": job_record.get("annotationData"),
            "lastError": job_record.get("lastError", ""),
        }
