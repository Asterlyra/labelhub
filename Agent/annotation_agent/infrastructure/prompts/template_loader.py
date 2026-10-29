"""提示词模板文件加载器。"""

from __future__ import annotations

from pathlib import Path


# 提示词模板文件根目录
TEMPLATE_ROOT = Path(__file__).resolve().parent / "templates"


class PromptTemplateLoader:
    """从文件系统安全加载提示词模板文件。"""

    def read_text(self, relative_path: str) -> str:
        """读取指定相对路径的模板文件内容（UTF-8编码）。"""
        return self._safe_path(relative_path).read_text(encoding="utf-8")

    def _safe_path(self, relative_path: str) -> Path:
        """验证并解析模板文件路径，防止目录遍历攻击。"""
        path = (TEMPLATE_ROOT / relative_path).resolve()
        root = TEMPLATE_ROOT.resolve()
        # 确保解析后的路径在模板根目录下
        if root not in path.parents and path != root:
            raise ValueError(f"Invalid prompt template path: {relative_path}")
        if not path.exists():
            raise FileNotFoundError(f"Prompt template not found: {relative_path}")
        return path
