"""提示词模板仓储。"""

from __future__ import annotations

from dataclasses import dataclass

from annotation_agent.infrastructure.prompts.template_loader import PromptTemplateLoader


@dataclass(frozen=True)
class PromptTemplate:
    """不可变的提示词模板数据对象。"""

    template_id: str  # 模板唯一标识
    content: str  # 模板文本内容


class PromptTemplateRepository:
    """提示词模板仓储，通过模板ID加载模板文件。"""

    def __init__(self, loader: PromptTemplateLoader | None = None) -> None:
        """初始化仓储，可注入自定义模板加载器。"""
        self.loader = loader or PromptTemplateLoader()

    def get(self, template_id: str) -> PromptTemplate:
        """根据模板ID加载并返回提示词模板对象。"""
        return PromptTemplate(
            template_id=template_id,
            content=self.loader.read_text(f"{template_id}.txt"),
        )
