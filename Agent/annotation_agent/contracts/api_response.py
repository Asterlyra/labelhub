"""共享API响应辅助函数。"""

from __future__ import annotations

from typing import Any


def success_response(data: Any = None, message: str = "success", code: str = "200") -> dict[str, Any]:
    """构造成功的API响应体。"""
    return {
        "code": code,
        "message": message,
        "data": data,
    }


def error_response(message: str, code: str = "500", data: Any = None) -> dict[str, Any]:
    """构造失败的API响应体。"""
    return {
        "code": code,
        "message": message,
        "data": data,
    }
