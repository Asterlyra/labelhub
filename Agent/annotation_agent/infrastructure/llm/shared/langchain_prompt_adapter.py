"""Helpers for rendering LangChain chat prompts into PromptMessages."""

from __future__ import annotations

from collections.abc import Iterable, Mapping
from typing import Any

from langchain_core.messages import BaseMessage
from langchain_core.prompts import ChatPromptTemplate

from annotation_agent.infrastructure.llm.shared.prompt_messages import PromptContentPart, PromptMessages


def render_prompt_messages(
    *,
    system_template: str,
    user_template: str,
    variables: Mapping[str, Any],
    user_parts: tuple[PromptContentPart, ...] = (),
) -> PromptMessages:
    """Render chat prompt templates while preserving literal braces in template text."""
    variable_names = tuple(variables.keys())
    prompt_template = ChatPromptTemplate.from_messages(
        [
            ("system", _escape_template_text(system_template, variable_names)),
            ("human", _escape_template_text(user_template, variable_names)),
        ]
    )
    rendered_messages = prompt_template.format_messages(**dict(variables))
    return prompt_messages_from_langchain(rendered_messages, user_parts=user_parts)


def prompt_messages_from_langchain(
    messages: Iterable[BaseMessage],
    *,
    user_parts: tuple[PromptContentPart, ...] = (),
) -> PromptMessages:
    """Convert rendered LangChain messages into the project's PromptMessages type."""
    system_prompt = "\n".join(
        _message_to_text(message)
        for message in messages
        if message.type == "system"
    ).strip()
    user_prompt = "\n".join(
        _message_to_text(message)
        for message in messages
        if message.type in {"human", "user"}
    ).strip()
    return PromptMessages(
        system_prompt=system_prompt,
        user_prompt=user_prompt,
        user_parts=user_parts,
    )


def _escape_template_text(template_text: str, variable_names: Iterable[str]) -> str:
    escaped_text = template_text
    placeholders: dict[str, str] = {}
    for index, variable_name in enumerate(variable_names):
        placeholder = f"{{{variable_name}}}"
        token = f"__LC_VAR_{index}__"
        placeholders[token] = placeholder
        escaped_text = escaped_text.replace(placeholder, token)
    escaped_text = escaped_text.replace("{", "{{").replace("}", "}}")
    for token, placeholder in placeholders.items():
        escaped_text = escaped_text.replace(token, placeholder)
    return escaped_text


def _message_to_text(message: BaseMessage) -> str:
    content = message.content
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        parts: list[str] = []
        for item in content:
            if isinstance(item, str):
                parts.append(item)
                continue
            if isinstance(item, dict):
                text = item.get("text")
                if isinstance(text, str):
                    parts.append(text)
                    continue
                image_url = item.get("image_url")
                if isinstance(image_url, dict) and isinstance(image_url.get("url"), str):
                    parts.append(f"[image] {image_url['url']}")
        return "\n".join(parts)
    return str(content)
