"""覆盖数据集 MQ 载荷生成和压测辅助逻辑的测试。"""

from __future__ import annotations

from pathlib import Path
import pytest

from annotation_agent.tooling.dataset_mq_payload_builder import build_dataset_bundle, write_dataset_bundle
from annotation_agent.tooling.mq_pressure_test_runner import (
    apply_result_snapshot,
    build_initial_record,
    prepare_run_message,
    summarize_records,
)


AGENT_DIR = Path(__file__).resolve().parents[2]
DATASETS_ROOT = AGENT_DIR.parent / "docs" / "datasets"


@pytest.mark.skipif(not DATASETS_ROOT.exists(), reason="Optional dataset is not included")
def test_build_dataset_bundle_matches_expected_counts():
    """保护数据源总量不被误改，避免生成结果缺失。"""
    bundle = build_dataset_bundle(DATASETS_ROOT)

    assert len(bundle.qa_events) == 30
    assert len(bundle.preference_events) == 12
    assert bundle.source_counts["all"] == 42


@pytest.mark.skipif(not DATASETS_ROOT.exists(), reason="Optional dataset is not included")
def test_generated_qa_event_uses_backend_message_shape():
    """保护 QA 样例生成的事件形状仍然符合后端消息契约。"""
    bundle = build_dataset_bundle(DATASETS_ROOT)
    event = bundle.qa_events[0]

    assert event["taskId"] == "dataset-qa-quality"
    assert event["taskItemId"].startswith("qa-quality-Q")
    assert "materials" in event
    assert "annotationData" in event
    assert event["reviewConfig"]["dimensions"]
    assert event["annotationData"]["相关性评分"] in {"4", "5"}


@pytest.mark.skipif(not DATASETS_ROOT.exists(), reason="Optional dataset is not included")
def test_generated_preference_event_preserves_preference_annotation():
    """保护偏好对比样例的关键人工标注字段不会丢失。"""
    bundle = build_dataset_bundle(DATASETS_ROOT)
    event = bundle.preference_events[7]

    assert event["taskId"] == "dataset-preference-compare"
    assert event["annotationData"]["偏好结论"] == "A"
    assert event["annotationData"]["安全风险"] == "是"
    assert "判断理由" in event["annotationData"]


@pytest.mark.skipif(not DATASETS_ROOT.exists(), reason="Optional dataset is not included")
def test_write_dataset_bundle_creates_manifest_and_jsonl(tmp_path):
    """保护生成器会把 manifest、json 和 jsonl 都正确落盘。"""
    bundle = build_dataset_bundle(DATASETS_ROOT)

    manifest = write_dataset_bundle(bundle, tmp_path)

    assert manifest["counts"]["all"] == 42
    assert (tmp_path / "all_mq_messages.jsonl").exists()
    assert (tmp_path / "reports").is_dir()


def test_prepare_run_message_rewrites_task_identity():
    """保护压测运行时一定会改写任务标识，避免覆盖历史结果。"""
    source_message = {
        "taskId": "dataset-qa-quality",
        "taskItemId": "qa-quality-Q0001",
    }

    message = prepare_run_message(source_message, "run-123", 7)

    assert message["taskId"] == "dataset-qa-quality--run-123"
    assert message["taskItemId"] == "qa-quality-Q0001--run-123--0007"
    assert message["pressureTestMeta"]["sourceTaskItemId"] == "qa-quality-Q0001"


def test_apply_result_snapshot_updates_record_and_summary():
    """保护 MySQL 结果快照能正确回填到本地压测记录。"""
    message = prepare_run_message({"taskId": "dataset", "taskItemId": "sample"}, "run-1", 1)
    record = build_initial_record(message, "all_mq_messages.jsonl")

    apply_result_snapshot(
        record,
        {
            "status": "completed",
            "audit_status": "pass",
            "attempt_count": 1,
            "summary": "ok",
            "last_error": "",
            "confidence": 0.9,
            "weighted_score": 88.0,
        },
        "2026-06-03T00:00:01.500Z",
    )

    summary = summarize_records([record])

    assert record["success"] is True
    assert record["status"] == "completed"
    assert record["latencyMs"] is not None
    assert summary["successCount"] == 1
