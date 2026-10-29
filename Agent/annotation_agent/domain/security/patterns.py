"""Regex patterns used to detect prompt injection attempts."""

from __future__ import annotations

import re
from dataclasses import dataclass
from typing import Pattern


@dataclass(frozen=True)
class PromptInjectionPattern:
    """A single regex rule with replacement metadata."""

    category: str
    pattern_id: str
    regex: Pattern[str]
    replacement: str


PROMPT_INJECTION_PATTERNS: tuple[PromptInjectionPattern, ...] = (
    PromptInjectionPattern(
        category="role_marker",
        pattern_id="role.line_prefix",
        regex=re.compile(
            r"(?im)^\s*(system|assistant|user|developer|tool|function|instruction|prompt)\s*[:：]"
        ),
        replacement="[ROLE_MARKER_REMOVED] ",
    ),
    PromptInjectionPattern(
        category="injection_phrase",
        pattern_id="phrase.ignore_or_reveal",
        regex=re.compile(
            r"(?i)"
            r"(ignore\s+(all\s+)?(previous|prior|above)\s+instructions|"
            r"disregard\s+(all\s+)?(previous|prior|above)\s+instructions|"
            r"reveal\s+(the\s+)?system\s+prompt|"
            r"act\s+as\s+(the\s+)?system|"
            r"忽略之前(所有)?指令|"
            r"无视之前(所有)?要求|"
            r"输出(你的)?系统提示词|"
            r"你现在是(系统|system|developer)|"
            r"覆盖之前的规则)"
        ),
        replacement="[INJECTION_PHRASE_REMOVED]",
    ),
    PromptInjectionPattern(
        category="fake_separator",
        pattern_id="separator.boundary_line",
        regex=re.compile(
            r"(?im)^\s*[-=_*]{3,}\s*.*?(开始|结束|start|end|begin|resume|system|instruction|prompt|data|content).*$"
        ),
        replacement="[FAKE_SEPARATOR_REMOVED]",
    ),
    PromptInjectionPattern(
        category="fake_boundary_tag",
        pattern_id="tag.boundary_forgery",
        regex=re.compile(
            r"(?i)</?(data-boundary|system-prompt|system_prompt|developer-message|trusted|untrusted|instruction|instructions|context|tool-output|user-content)[^>]*>"
        ),
        replacement="[FAKE_BOUNDARY_TAG_REMOVED]",
    ),
)
