"""审核校验规则执行链。"""

from __future__ import annotations

from typing import Any

from annotation_agent.domain.audit.rules.json_field_rule import JsonFieldRule
from annotation_agent.domain.audit.rules.multi_choice_rule import MultiChoiceRule
from annotation_agent.domain.audit.rules.required_field_rule import RequiredFieldRule
from annotation_agent.domain.audit.rules.score_range_rule import ScoreRangeRule
from annotation_agent.domain.audit.rules.single_choice_rule import SingleChoiceRule
from annotation_agent.domain.audit.rules.validation_rule import ValidationRule, is_empty
from annotation_agent.domain.shared.task_schema import SchemaField


class RuleValidationChain:
    """按优先级依次执行校验规则：先检查必填，再执行类型相关规则。"""

    def __init__(self, rules: list[ValidationRule] | None = None) -> None:
        # 必填规则优先级最高，单独执行
        self.required_rule = RequiredFieldRule()
        # 非必填校验规则列表
        self.rules = rules or [
            SingleChoiceRule(),
            MultiChoiceRule(),
            JsonFieldRule(),
            ScoreRangeRule(),
        ]

    @classmethod
    def default(cls) -> "RuleValidationChain":
        """创建包含默认规则集的校验链。"""
        return cls()

    def validate(self, field: SchemaField, value: Any, annotation_result: dict[str, Any]) -> list[dict[str, Any]]:
        """对单个字段执行完整校验链，返回所有问题。"""
        # 首先检查必填字段
        required_issues = self.required_rule.validate(field, value, annotation_result)
        if required_issues:
            return required_issues
        # 值为空且非必填，跳过后续规则
        if is_empty(value):
            return []

        # 依次执行类型相关校验规则
        issues: list[dict[str, Any]] = []
        for rule in self.rules:
            issues.extend(rule.validate(field, value, annotation_result))
        return issues
