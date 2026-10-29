"""测试审核输出校验器。"""

import re

import pytest

from annotation_agent.infrastructure.llm.audit.output_validator import OutputValidator
from annotation_agent.infrastructure.llm.shared.output_validation import OutputValidationError


# 测试用审核维度配置
DIMENSIONS = [
    {"field": "relevance", "weight": 0.4, "passScore": 80},
    {"field": "accuracy", "weight": 0.4, "passScore": 85},
]


# 验证校验器接受合法的JSON输出
def test_output_validator_accepts_valid_json():
    validator = OutputValidator()
    raw_output = """
{
  "scores": [
    {"dimension": "relevance", "score": 90, "reason": "Answer stays on topic."},
    {"dimension": "accuracy", "score": 88, "reason": "Facts are mostly correct."}
  ],
  "summary": "Audit passed.",
  "confidence": 0.91
}
"""

    result = validator.parse_and_validate(raw_output, DIMENSIONS)

    assert result["confidence"] == 0.91
    assert [item["dimension"] for item in result["scores"]] == ["relevance", "accuracy"]


@pytest.mark.parametrize(
    ("raw_output", "expected_error"),
    [
        ("not json", "LLM output is not valid JSON"),
        ('{"summary": "x", "confidence": 0.8}', "missing required fields: scores"),
        (
            '{"scores": [{"dimension": "relevance", "score": 90, "reason": "ok"}], '
            '"summary": "x", "confidence": 0.8}',
            "missing score for dimension: accuracy",
        ),
        (
            '{"scores": [{"dimension": "relevance", "score": 90, "reason": "ok"}, '
            '{"dimension": "accuracy", "score": 80, "reason": "ok"}], '
            '"summary": "x", "confidence": 1.2}',
            "confidence must be between 0 and 1",
        ),
        (
            '{"scores": [{"dimension": "relevance", "score": 90, "reason": "ok"}, '
            '{"dimension": "relevance", "score": 80, "reason": "dup"}], '
            '"summary": "x", "confidence": 0.8}',
            "duplicate score dimension: relevance",
        ),
        (
            '{"scores": [{"dimension": "relevance", "score": 90, "reason": "ok"}, '
            '{"dimension": "accuracy", "score": 80, "reason": "ok"}], '
            '"summary": "   ", "confidence": 0.8}',
            "summary must be a non-empty string",
        ),
    ],
)
# 验证校验器拒绝各种非法输出并抛出预期错误
def test_output_validator_rejects_invalid_outputs(raw_output, expected_error):
    validator = OutputValidator()

    with pytest.raises(OutputValidationError, match=re.escape(expected_error)):
        validator.parse_and_validate(raw_output, DIMENSIONS)
