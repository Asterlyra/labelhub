"""把 docs/datasets 下的样例数据转换成后端风格的 RabbitMQ 审核事件。"""

from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any


QA_TASK_ID = "dataset-qa-quality"
PREFERENCE_TASK_ID = "dataset-preference-compare"
QA_SOURCE = "qa_quality/json/qa_quality.json"
PREFERENCE_SOURCE = "preference_compare/json/preference_compare.json"

QA_FIELD_BY_DIMENSION = {
    "相关性": "相关性评分",
    "准确性": "准确性评分",
    "格式合规": "格式合规评分",
    "安全性": "安全性评分",
}


@dataclass(frozen=True)
class DatasetBundle:
    """生成后的事件集合与来源统计信息。"""

    qa_events: list[dict[str, Any]]
    preference_events: list[dict[str, Any]]
    source_counts: dict[str, int]

    @property
    def all_events(self) -> list[dict[str, Any]]:
        return [*self.qa_events, *self.preference_events]


def build_dataset_bundle(datasets_root: Path) -> DatasetBundle:
    """加载数据集目录，并转换成可直接发往 Agent 的后端风格审核事件。"""
    qa_records = _load_json_array(datasets_root / QA_SOURCE)
    preference_records = _load_json_array(datasets_root / PREFERENCE_SOURCE)
    qa_events = [_build_qa_event(item) for item in qa_records]
    preference_events = [_build_preference_event(item) for item in preference_records]
    return DatasetBundle(
        qa_events=qa_events,
        preference_events=preference_events,
        source_counts={
            "qa_quality": len(qa_events),
            "preference_compare": len(preference_events),
            "all": len(qa_events) + len(preference_events),
        },
    )


def write_dataset_bundle(bundle: DatasetBundle, output_dir: Path) -> dict[str, Any]:
    """把生成好的事件和清单文件写入 Agent/tests/fixtures/generated。"""
    output_dir.mkdir(parents=True, exist_ok=True)
    report_dir = output_dir / "reports"
    report_dir.mkdir(parents=True, exist_ok=True)

    qa_path = output_dir / "qa_quality_mq_messages.json"
    preference_path = output_dir / "preference_compare_mq_messages.json"
    all_json_path = output_dir / "all_mq_messages.json"
    all_jsonl_path = output_dir / "all_mq_messages.jsonl"
    manifest_path = output_dir / "manifest.json"

    _write_json(qa_path, bundle.qa_events)
    _write_json(preference_path, bundle.preference_events)
    _write_json(all_json_path, bundle.all_events)
    _write_jsonl(all_jsonl_path, bundle.all_events)

    manifest = {
        "sources": {
            "qa_quality": QA_SOURCE,
            "preference_compare": PREFERENCE_SOURCE,
        },
        "counts": bundle.source_counts,
        "outputs": {
            "qa_quality": qa_path.name,
            "preference_compare": preference_path.name,
            "all_json": all_json_path.name,
            "all_jsonl": all_jsonl_path.name,
            "reports_dir": report_dir.name,
        },
    }
    _write_json(manifest_path, manifest)
    return manifest


def _load_json_array(path: Path) -> list[dict[str, Any]]:
    """读取 JSON 数组文件，并过滤掉非对象项。"""
    raw = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(raw, list):
        raise ValueError(f"dataset file must be a JSON array: {path}")
    return [item for item in raw if isinstance(item, dict)]


def _build_qa_event(item: dict[str, Any]) -> dict[str, Any]:
    """把单条 QA 质量样例转换成一条后端审核事件。"""
    dimensions = _build_qa_dimensions(item.get("expected_dimensions"))
    scores = _build_qa_scores(item, dimensions)
    issue_tags = _build_issue_tags(item)

    annotation_data = {
        "相关性评分": str(scores["相关性评分"]),
        "准确性评分": str(scores["准确性评分"]),
        "格式合规评分": str(scores["格式合规评分"]),
        "安全性评分": str(scores["安全性评分"]),
        "问题类型标签": issue_tags,
        "一句话总评": _qa_summary(item, dimensions),
        "详细评语": _qa_detail(item),
    }

    return {
        "taskItemId": f"qa-quality-{item['id']}",
        "taskId": QA_TASK_ID,
        "taskTitle": "QA quality dataset audit replay",
        "taskDescription": "Replay docs/datasets/qa_quality records as backend audit messages.",
        "materials": _qa_materials(),
        "reviewConfig": {
            "reviewPrompt": "请根据原始题面、模型回答、参考答案和人工标注结果，判断该质量标注是否合理。",
            "dimensions": dimensions,
            "passThreshold": 85,
            "reviewThreshold": 70,
        },
        "rawData": dict(item),
        "annotationData": annotation_data,
    }


def _build_preference_event(item: dict[str, Any]) -> dict[str, Any]:
    """把单条偏好对比样例转换成一条后端审核事件。"""
    safety_value = "是" if item.get("safety_flag") else "否"
    preferred = str(item.get("preferred") or "tie")
    margin = str(item.get("margin") or "相当")
    annotation_data = {
        "偏好结论": preferred,
        "优势程度": margin,
        "判断依据维度": _ensure_string_list(item.get("dimensions")),
        "安全风险": safety_value,
        "一句话结论": _preference_summary(item),
        "判断理由": str(item.get("annotator_note") or "").strip(),
    }

    return {
        "taskItemId": f"preference-compare-{item['id']}",
        "taskId": PREFERENCE_TASK_ID,
        "taskTitle": "Preference compare dataset audit replay",
        "taskDescription": "Replay docs/datasets/preference_compare records as backend audit messages.",
        "materials": _preference_materials(item),
        "reviewConfig": {
            "reviewPrompt": "请根据原始问题、A/B 回答和人工偏好标注，判断该偏好标注是否合理。",
            "dimensions": [
                {"field": "偏好结论", "weight": 40, "passScore": 85},
                {"field": "优势程度", "weight": 15, "passScore": 75},
                {"field": "判断依据维度", "weight": 15, "passScore": 75},
                {"field": "安全风险", "weight": 10, "passScore": 80},
                {"field": "判断理由", "weight": 20, "passScore": 75},
            ],
            "passThreshold": 85,
            "reviewThreshold": 70,
        },
        "rawData": dict(item),
        "annotationData": annotation_data,
    }


def _build_qa_dimensions(expected_dimensions: Any) -> list[dict[str, Any]]:
    """把数据集里的评估维度映射成 Agent 可识别的审核维度字段。"""
    names = [name for name in _ensure_string_list(expected_dimensions) if name in QA_FIELD_BY_DIMENSION]
    if not names:
        names = ["相关性", "准确性"]
    weight = round(100 / len(names), 2)
    return [
        {
            "field": QA_FIELD_BY_DIMENSION[name],
            "weight": weight,
            "passScore": 80,
        }
        for name in names
    ]


def _build_qa_scores(item: dict[str, Any], dimensions: list[dict[str, Any]]) -> dict[str, int]:
    """为质量审核数据构造一份稳定、可回放的人工标注结果。"""
    active_fields = {dimension["field"] for dimension in dimensions}
    answer_text = str(item.get("model_answer") or "")
    prompt_text = str(item.get("prompt") or "")
    safety_score = 5
    # 对明显的安全类题目做一点启发式分支，避免所有样例都长得一样。
    if "怎么绕过" in prompt_text or "自制烟花" in prompt_text:
        safety_score = 5 if ("抱歉" in answer_text or "无法" in answer_text) else 1

    scores = {
        "相关性评分": 5 if "相关性评分" in active_fields else 4,
        "准确性评分": 5 if "准确性评分" in active_fields else 4,
        "格式合规评分": 5 if "格式合规评分" in active_fields else 4,
        "安全性评分": safety_score if "安全性评分" in active_fields else max(4, safety_score),
    }

    # 多模态与极短回答在格式维度上更容易触发人工关注，分数略保守一些。
    if str(item.get("media_type") or "") in {"image", "video", "markdown"}:
        scores["格式合规评分"] = min(scores["格式合规评分"], 4)
    if len(answer_text) < 16:
        scores["格式合规评分"] = min(scores["格式合规评分"], 4)
    return scores


def _build_issue_tags(item: dict[str, Any]) -> list[str]:
    """根据样例内容补一组较轻量的问题标签，方便审核链路有结构化输入。"""
    tags: list[str] = []
    prompt_text = str(item.get("prompt") or "")
    answer_text = str(item.get("model_answer") or "")
    if "不超过 60 字" in prompt_text and len(answer_text) > 70:
        tags.append("字数控制")
    if ("怎么绕过" in prompt_text or "自制烟花" in prompt_text) and not ("抱歉" in answer_text or "无法" in answer_text):
        tags.append("安全风险")
    return tags


def _qa_materials() -> list[dict[str, Any]]:
    """构造 QA 质量任务的后端 materials 定义。"""
    return [
        _radio_material("相关性评分", ["1", "2", "3", "4", "5"]),
        _radio_material("准确性评分", ["1", "2", "3", "4", "5"]),
        _radio_material("格式合规评分", ["1", "2", "3", "4", "5"]),
        _radio_material("安全性评分", ["1", "2", "3", "4", "5"]),
        {
            "field": "问题类型标签",
            "label": "下拉选项框",
            "required": 0,
            "config": {
                "options": ["事实错误", "答非所问", "格式问题", "安全风险", "信息缺失", "字数控制"],
                "multiple": True,
            },
        },
        _textarea_material("一句话总评"),
        _textarea_material("详细评语"),
    ]


def _preference_materials(item: dict[str, Any]) -> list[dict[str, Any]]:
    """构造偏好对比任务的后端 materials 定义。"""
    return [
        _radio_material("偏好结论", ["A", "B", "tie"]),
        _radio_material("优势程度", ["明显优于", "略优于", "相当"]),
        {
            "field": "判断依据维度",
            "label": "下拉选项框",
            "required": 1,
            "config": {
                "options": _ensure_string_list(item.get("dimensions")) or ["准确性", "完整性"],
                "multiple": True,
            },
        },
        _radio_material("安全风险", ["是", "否"]),
        _textarea_material("一句话结论"),
        _textarea_material("判断理由"),
    ]


def _radio_material(field: str, options: list[str]) -> dict[str, Any]:
    """生成一个单选组件定义。"""
    return {
        "field": field,
        "label": "单选框",
        "required": 1,
        "config": {"options": options},
    }


def _textarea_material(field: str) -> dict[str, Any]:
    """生成一个多行文本组件定义。"""
    return {
        "field": field,
        "label": "详情页内容",
        "required": 1,
        "config": {"maxLength": 4000},
    }


def _qa_summary(item: dict[str, Any], dimensions: list[dict[str, Any]]) -> str:
    """生成质量审核的一句话总评。"""
    dimension_names = [dimension["field"] for dimension in dimensions]
    return (
        f"围绕 {str(item.get('id') or '')} 的质量标注整体合理，重点覆盖"
        f"{'、'.join(dimension_names)}。"
    )


def _qa_detail(item: dict[str, Any]) -> str:
    """生成质量审核的详细评语。"""
    reference = str(item.get("reference") or "").strip()
    answer = str(item.get("model_answer") or "").strip()
    return f"模型回答：{answer}；参考依据：{reference}。人工标注可作为审核输入。"


def _preference_summary(item: dict[str, Any]) -> str:
    """生成偏好对比任务的一句话结论。"""
    preferred = str(item.get("preferred") or "tie")
    if preferred == "tie":
        return "两条回答整体水平接近，人工标注判平局。"
    return f"{preferred} 在主要维度上更优，人工偏好结论明确。"


def _ensure_string_list(value: Any) -> list[str]:
    """把任意列表输入规整成去空字符串列表。"""
    if not isinstance(value, list):
        return []
    return [str(item).strip() for item in value if str(item).strip()]


def _write_json(path: Path, payload: Any) -> None:
    """以 UTF-8 JSON 形式写文件，保留中文。"""
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def _write_jsonl(path: Path, records: list[dict[str, Any]]) -> None:
    """把事件序列写成 JSONL，便于逐行回放。"""
    body = "".join(json.dumps(record, ensure_ascii=False) + "\n" for record in records)
    path.write_text(body, encoding="utf-8")
