"""审核模型调用前的确定性校验。"""

from __future__ import annotations

from typing import Any

from annotation_agent.domain.shared.task_schema import SchemaField
from annotation_agent.domain.audit.rules.rule_validation_chain import RuleValidationChain


class AnnotationRuleValidator:
    """在调用 LLM 审核前，对标注结果执行确定性规则校验。"""

    def __init__(self, chain: RuleValidationChain | None = None) -> None:
        # 使用传入的校验链，或创建默认校验链
        self.chain = chain or RuleValidationChain.default()

    def validate(
        self,
        fields: list[SchemaField],
        annotation_result: dict[str, Any] | None,
    ) -> list[dict[str, Any]]:
        """对所有字段逐一执行校验规则，返回发现的问题列表。"""
        result = annotation_result or {}
        issues: list[dict[str, Any]] = []
        for field in fields:
            issues.extend(self.chain.validate(field, result.get(field.field), result))
        return issues
