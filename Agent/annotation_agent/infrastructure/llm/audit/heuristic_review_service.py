"""审核链路的启发式评审服务。"""

from __future__ import annotations

from difflib import SequenceMatcher
from typing import Any


class HeuristicReviewService:
    """在 mock 模式下，用规则近似模拟审核过程。"""

    def review(
        self,
        raw_data: dict[str, Any],
        annotation_result: dict[str, Any],
        dimensions: list[dict[str, Any]],
        validation_issues: list[dict[str, Any]],
        annotation_schema: list[dict[str, Any]] | None = None,
    ) -> dict[str, Any]:
        """根据任务类型自动选择合适的启发式审核策略。"""
        if self._looks_like_preference_compare(raw_data, annotation_result):
            return self._review_preference_compare(raw_data, annotation_result, dimensions, validation_issues)
        if self._looks_like_quality_review(raw_data, annotation_result, annotation_schema or []):
            return self._review_quality(raw_data, annotation_result, dimensions, validation_issues)
        return self._review_generic(annotation_result, dimensions, validation_issues)

    def _review_quality(
        self,
        raw_data: dict[str, Any],
        annotation_result: dict[str, Any],
        dimensions: list[dict[str, Any]],
        validation_issues: list[dict[str, Any]],
    ) -> dict[str, Any]:
        """质量评审：检查答案与参考答案、分数和评语是否一致。"""
        answer = self._first_raw_value(raw_data, "model_answer", "answer", "response", "summary", "content", "output")
        reference = self._first_raw_value(raw_data, "reference", "reference_answer", "标准答案", "参考答案")
        overlap = self._similarity(answer, reference)
        issues = list(validation_issues)

        relevance_value = self._find_score(annotation_result, "relevance", "相关")
        accuracy_value = self._find_score(annotation_result, "accuracy", "准确")
        comment = self._find_text(annotation_result, "comment", "note", "评语", "理由")

        if relevance_value is not None and relevance_value <= 2 and answer:
            issues.append(
                self._issue(
                    "relevance_score",
                    "medium",
                    "答案明显回应了问题，但相关性评分异常偏低。",
                    "请重新检查相关性评分，或更明确地记录扣分原因。",
                )
            )
        if accuracy_value is not None and overlap >= 0.18 and accuracy_value <= 3:
            issues.append(
                self._issue(
                    "accuracy_score",
                    "medium",
                    "答案与参考答案重合度较高，但准确度评分异常偏低。",
                    "请核实扣分背后是否存在未说明的事实错误。",
                )
            )
        if comment and accuracy_value and accuracy_value >= 4 and self._contains_negative_judgement(comment):
            issues.append(
                self._issue(
                    "comment",
                    "medium",
                    "评语明显偏负面，但准确度评分较高，存在矛盾。",
                    "请使书面评语与评分保持一致。",
                )
            )

        base_score = 92 if overlap >= 0.18 else 78
        return self._build_result(dimensions, issues, base_score, "启发式模拟审核完成质量评审。")

    def _review_preference_compare(
        self,
        raw_data: dict[str, Any],
        annotation_result: dict[str, Any],
        dimensions: list[dict[str, Any]],
        validation_issues: list[dict[str, Any]],
    ) -> dict[str, Any]:
        """偏好对比评审：检查选择结论和书面理由是否一致。"""
        response_a = str(raw_data.get("response_a", ""))
        response_b = str(raw_data.get("response_b", ""))
        preferred = annotation_result.get("preferred")
        note = str(annotation_result.get("annotator_note") or annotation_result.get("note") or "")
        issues = list(validation_issues)

        if preferred in {"A", "B"} and not note:
            issues.append(
                self._issue(
                    "annotator_note",
                    "high",
                    "偏好对比任务需要提供明确的理由说明。",
                    "请解释为什么所选回答更好。",
                )
            )

        if preferred == "A" and note and self._mentions_b_better(note):
            issues.append(
                self._issue(
                    "preferred",
                    "medium",
                    "结论选择了 A，但评语内容似乎更支持 B。",
                    "请使 `preferred` 与书面评语保持一致。",
                )
            )
        if preferred == "B" and note and self._mentions_a_better(note):
            issues.append(
                self._issue(
                    "preferred",
                    "medium",
                    "结论选择了 B，但评语内容似乎更支持 A。",
                    "请使 `preferred` 与书面评语保持一致。",
                )
            )

        length_gap = abs(len(response_a) - len(response_b))
        base_score = 88 if preferred in {"A", "B", "tie"} and note else 74
        if length_gap > 30 and preferred in {"A", "B"}:
            base_score += 4
        return self._build_result(
            dimensions,
            issues,
            min(base_score, 96),
            "启发式模拟审核完成偏好对比评审。",
        )

    def _review_generic(
        self,
        annotation_result: dict[str, Any],
        dimensions: list[dict[str, Any]],
        validation_issues: list[dict[str, Any]],
    ) -> dict[str, Any]:
        """通用评审：没有特殊规则时给出基础评分。"""
        base_score = 86 if annotation_result else 65
        return self._build_result(
            dimensions,
            list(validation_issues),
            base_score,
            "启发式模拟审核完成通用标注评审。",
        )

    @staticmethod
    def _looks_like_preference_compare(raw_data: dict[str, Any], annotation_result: dict[str, Any]) -> bool:
        """判断是否是偏好对比类任务。"""
        return "response_a" in raw_data or "response_b" in raw_data or "preferred" in annotation_result

    @staticmethod
    def _looks_like_quality_review(
        raw_data: dict[str, Any],
        annotation_result: dict[str, Any],
        annotation_schema: list[dict[str, Any]],
    ) -> bool:
        """判断是否是质量评分类任务。"""
        if any(key in raw_data for key in ("reference", "reference_answer", "model_answer", "answer", "response")):
            return True
        for field in annotation_schema:
            if "score" in str(field.get("field", "")).lower():
                return True
        return any("score" in str(key).lower() for key in annotation_result)

    def _build_result(
        self,
        dimensions: list[dict[str, Any]],
        issues: list[dict[str, Any]],
        base_score: int,
        summary: str,
    ) -> dict[str, Any]:
        """根据问题严重程度计算各维度分数。"""
        penalty = min(
            35,
            len([item for item in issues if item.get("level") == "high"]) * 18
            + len([item for item in issues if item.get("level") != "high"]) * 8,
        )
        final_base = max(40, base_score - penalty)
        scores = []
        for dimension in dimensions:
            name = str(dimension.get("field") or dimension.get("name") or "").strip()
            if not name:
                continue
            score = final_base
            if name in {"safety_compliance", "format_compliance", "安全性", "格式合规"} and not issues:
                score = min(100, final_base + 3)
            scores.append(
                {
                    "dimension": name,
                    "score": score,
                    "reason": f"启发式模拟审核根据模式、结果和确定性校验，对{name}评分{score}分。",
                }
            )

        final_summary = summary if not issues else f"{summary} 发现{len(issues)}个需要关注的问题。"
        return {
            "scores": scores,
            "summary": final_summary,
            "confidence": 0.93 if not issues else 0.78,
        }

    @staticmethod
    def _similarity(left: str, right: str) -> float:
        """计算两个字符串的相似度。"""
        if not left or not right:
            return 0.0
        direct = SequenceMatcher(None, left, right).ratio()
        shared_chars = set(left) & set(right)
        coverage = len(shared_chars) / max(len(set(right)), 1)
        return max(direct, coverage)

    @staticmethod
    def _find_score(annotation_result: dict[str, Any], *keywords: str) -> float | None:
        """在标注结果中查找匹配关键字的评分字段。"""
        for key, value in annotation_result.items():
            lower_key = str(key).lower()
            if any(keyword.lower() in lower_key for keyword in keywords):
                try:
                    return float(value)
                except (TypeError, ValueError):
                    return None
        return None

    @staticmethod
    def _find_text(annotation_result: dict[str, Any], *keywords: str) -> str:
        """在标注结果中查找匹配关键字的文本字段。"""
        for key, value in annotation_result.items():
            lower_key = str(key).lower()
            if any(keyword.lower() in lower_key for keyword in keywords):
                return str(value)
        return ""

    @staticmethod
    def _first_raw_value(raw_data: dict[str, Any], *fields: str) -> str:
        """按优先级返回原始数据中的第一个非空值。"""
        for field in fields:
            value = raw_data.get(field)
            if value not in (None, "", []):
                return str(value)
        return ""

    @staticmethod
    def _contains_negative_judgement(text: str) -> bool:
        """判断评语里是否包含明显负面判断。"""
        keywords = ["错误", "不正确", "不准确", "答非所问", "没有提到", "wrong", "incorrect", "missing"]
        return any(word in text for word in keywords)

    @staticmethod
    def _mentions_b_better(text: str) -> bool:
        """判断文本是否暗示 B 更好。"""
        keywords = ["更好", "更完整", "更准确", "优于", "better", "stronger"]
        return "B" in text and any(word in text for word in keywords)

    @staticmethod
    def _mentions_a_better(text: str) -> bool:
        """判断文本是否暗示 A 更好。"""
        keywords = ["更好", "更完整", "更准确", "优于", "better", "stronger"]
        return "A" in text and any(word in text for word in keywords)

    @staticmethod
    def _issue(field: str, level: str, message: str, suggestion: str) -> dict[str, Any]:
        """构造问题项。"""
        return {
            "field": field,
            "level": level,
            "message": message,
            "suggestion": suggestion,
        }
