"""审核专用执行上下文。"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any

from annotation_agent.contracts.audit_request import AuditRequest
from annotation_agent.domain.audit.audit_config import AuditConfig
from annotation_agent.domain.audit.media_models import PreparedMediaEvidence
from annotation_agent.domain.shared.task_schema import SchemaField
from annotation_agent.infrastructure.llm.shared.prompt_messages import PromptMessages


@dataclass
class EvidenceBundle:
    """与当前标注项关联的证据集合。"""
    task_kind: str  # 任务类型
    items: list[dict[str, Any]]  # 证据条目列表
    summary: str  # 证据摘要

    def to_prompt_payload(self) -> dict[str, Any]:
        """转换为可嵌入 prompt 的字典载荷。"""
        return {
            "taskKind": self.task_kind,
            "items": self.items,
            "summary": self.summary,
        }


@dataclass
class AuditContext:
    """审核流程的完整执行上下文，聚合请求、配置、校验结果与证据。"""
    request: AuditRequest  # 审核请求
    config: AuditConfig  # 审核配置
    schema_fields: list[SchemaField]  # 标注模式字段
    validation_issues: list[dict[str, Any]]  # 规则校验发现的问题
    evidence_bundle: EvidenceBundle  # 关联证据
    prepared_media_evidence: PreparedMediaEvidence | None  # 多媒体证据
    prompt_messages: PromptMessages  # LLM 提示消息
    prompt: str  # 最终拼装的提示词
    input_hash: str  # 输入哈希，用于幂等判断

    @property
    def dimensions(self) -> list[dict[str, Any]]:
        """返回所有审核维度的字典列表。"""
        return [dimension.to_dict() for dimension in self.config.dimensions]

    def to_llm_context(self) -> dict[str, Any]:
        """构建供 LLM 使用的完整上下文字典。"""
        return {
            "rawData": self.request.raw_data,
            "annotationSchema": [field.to_dict() for field in self.schema_fields],
            "annotationResult": self.request.annotation_result,
            "validationIssues": self.validation_issues,
            "reviewConfig": self.config.to_dict(),
            "evidence": self.evidence_bundle.to_prompt_payload(),
            "mediaResult": self.prepared_media_evidence.to_media_result() if self.prepared_media_evidence else None,
        }
