"""Domain models for prompt injection detection and sanitization."""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any


@dataclass(frozen=True)
class PromptInjectionFinding:
    """A single suspicious pattern match found in untrusted content."""

    category: str
    pattern_id: str
    field_path: str
    matched_text: str
    replacement: str


@dataclass(frozen=True)
class GuardResult:
    """Result returned by prompt injection guard operations."""

    sanitized_value: Any
    findings: tuple[PromptInjectionFinding, ...] = field(default_factory=tuple)
    blocked: bool = False
    risk_level: str = "none"
    reason: str = ""

