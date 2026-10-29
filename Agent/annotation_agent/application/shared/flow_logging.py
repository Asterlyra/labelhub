"""流程日志摘要工具。"""

from __future__ import annotations

from collections.abc import Mapping, Sequence
from typing import Any


def elapsed_ms(started_at: float, now: float) -> int:
    """计算毫秒级耗时。"""
    return int((now - started_at) * 1000)


def summarize_payload(payload: Any, keys: Sequence[str] | None = None) -> str:
    """将输入/输出压缩成适合终端查看的摘要。"""
    if not isinstance(payload, Mapping):
        return summarize_value(payload)

    selected_keys = list(keys) if keys is not None else list(payload.keys())
    parts = []
    for key in selected_keys:
        if key in payload:
            parts.append(f"{key}={summarize_value(payload[key])}")
    missing_count = max(len(payload) - len(parts), 0)
    if missing_count:
        parts.append(f"其他字段={missing_count}个")
    return "; ".join(parts) if parts else "空对象"


def summarize_value(value: Any) -> str:
    """输出不包含敏感正文的值摘要。"""
    if value is None:
        return "空"
    if isinstance(value, Mapping):
        keys = list(value.keys())
        preview = ",".join(str(key) for key in keys[:5])
        suffix = "..." if len(keys) > 5 else ""
        return f"对象(字段数={len(keys)}, 字段={preview}{suffix})"
    if isinstance(value, list):
        return f"数组(长度={len(value)})"
    if isinstance(value, tuple):
        return f"元组(长度={len(value)})"
    if isinstance(value, str):
        return f"文本(长度={len(value)})"
    if isinstance(value, bool):
        return str(value)
    if isinstance(value, (int, float)):
        return str(value)
    return type(value).__name__
