"""聊天式 LLM 提示消息。"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any


@dataclass(frozen=True)
class PromptContentPart:
    """单个多模态消息片段。"""

    type: str
    value: str

    @classmethod
    def image_url_part(cls, url: str) -> "PromptContentPart":
        return cls(type="image_url", value=url)

    def to_openai_content(self) -> dict[str, Any]:
        if self.type == "image_url":
            return {"type": "image_url", "image_url": {"url": self.value}}
        return {"type": "text", "text": self.value}

    def to_text_line(self) -> str:
        if self.type == "image_url":
            return f"[image] {self.value}"
        return self.value


@dataclass(frozen=True)
class PromptMessages:
    """将系统提示词和用户提示词封装为不可变消息对象。"""

    system_prompt: str
    user_prompt: str
    user_parts: tuple[PromptContentPart, ...] = field(default_factory=tuple)

    def user_prompt_with_attachments(self) -> str:
        """返回带附件摘要的用户提示词。"""
        if not self.user_parts:
            return self.user_prompt
        attachment_summary = "\n".join(part.to_text_line() for part in self.user_parts)
        return f"{self.user_prompt}\n\n附件证据：\n{attachment_summary}"

    def as_chat_messages(self) -> list[dict[str, Any]] | list[tuple[str, str]]:
        """转换为 ChatOpenAI 可直接消费的消息结构。"""
        if not self.user_parts:
            return [
                ("system", self.system_prompt),
                ("human", self.user_prompt),
            ]
        user_content: list[dict[str, Any]] = [{"type": "text", "text": self.user_prompt}]
        user_content.extend(part.to_openai_content() for part in self.user_parts)
        return [
            {"role": "system", "content": self.system_prompt},
            {"role": "user", "content": user_content},
        ]

    def as_text_only_chat_messages(self) -> list[tuple[str, str]]:
        """在 provider 不支持多模态时，退化为纯文本消息。"""
        return [
            ("system", self.system_prompt),
            ("human", self.user_prompt_with_attachments()),
        ]

    def to_text(self) -> str:
        """把系统提示词与用户提示词拼接成纯文本。"""
        return f"{self.system_prompt}\n\n{self.user_prompt_with_attachments()}"
