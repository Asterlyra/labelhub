"""LabelHub Agent 对外包入口。"""

from __future__ import annotations

from typing import Any

__all__ = ["create_app"]


def create_app(*args: Any, **kwargs: Any):
    """惰性导入 Flask 启动逻辑，避免工具脚本被迫加载整条运行时依赖链。"""
    from annotation_agent.app.bootstrap import create_app as _create_app

    return _create_app(*args, **kwargs)
