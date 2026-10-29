"""请求幂等性辅助服务。"""

from __future__ import annotations

import hashlib
import json
from typing import Any


# 幂等性服务：通过请求载荷的哈希值判断是否为重复请求
class IdempotencyService:
    # 对载荷进行规范化 JSON 序列化后计算 SHA-256 哈希
    def build_payload_hash(self, payload: dict[str, Any]) -> str:
        # 规范化：排序键、去除多余空白，确保相同内容产生相同哈希
        canonical_payload = json.dumps(payload, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
        return hashlib.sha256(canonical_payload.encode("utf-8")).hexdigest()

    # 组合 task_id 与 input_hash 生成幂等键
    def build_key(self, task_id: str, input_hash: str) -> str:
        return f"{task_id}:{input_hash}"
