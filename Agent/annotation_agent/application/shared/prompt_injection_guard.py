"""Utilities for sanitizing untrusted prompt inputs before LLM use."""

from __future__ import annotations

from typing import Any

from annotation_agent.domain.security.models import GuardResult, PromptInjectionFinding
from annotation_agent.domain.security.patterns import PROMPT_INJECTION_PATTERNS, PromptInjectionPattern


class PromptInjectionGuard:
    """Detects suspicious prompt-like content in untrusted text payloads."""

    def sanitize_text(self, text: str, source: str, field_path: str) -> GuardResult:
        normalized = text if isinstance(text, str) else str(text or "")
        sanitized = normalized
        findings: list[PromptInjectionFinding] = []
        for pattern in PROMPT_INJECTION_PATTERNS:
            matches = list(pattern.regex.finditer(sanitized))
            if not matches:
                continue
            findings.extend(self._build_findings(matches, pattern, field_path))
            sanitized = pattern.regex.sub(pattern.replacement, sanitized)
        return GuardResult(
            sanitized_value=sanitized,
            findings=tuple(findings),
            blocked=False,
            risk_level=self._risk_level(findings, source),
        )

    def sanitize_payload(self, payload: Any, source: str, field_path: str = "") -> GuardResult:
        findings: list[PromptInjectionFinding] = []
        sanitized_value = self._sanitize_payload_recursive(payload, source, field_path, findings)
        return GuardResult(
            sanitized_value=sanitized_value,
            findings=tuple(findings),
            blocked=False,
            risk_level=self._risk_level(findings, source),
        )

    def validate_config_text(self, text: str, source: str, field_path: str) -> GuardResult:
        sanitized_result = self.sanitize_text(text, source, field_path)
        blocked = bool(sanitized_result.findings)
        categories = ", ".join(sorted({item.category for item in sanitized_result.findings}))
        reason = ""
        if blocked:
            reason = f"blocked suspicious prompt content in {field_path}: {categories}"
        return GuardResult(
            sanitized_value=sanitized_result.sanitized_value,
            findings=sanitized_result.findings,
            blocked=blocked,
            risk_level="high" if blocked else sanitized_result.risk_level,
            reason=reason,
        )

    def _sanitize_payload_recursive(
        self,
        payload: Any,
        source: str,
        field_path: str,
        findings: list[PromptInjectionFinding],
    ) -> Any:
        if isinstance(payload, str):
            result = self.sanitize_text(payload, source, field_path)
            findings.extend(result.findings)
            return result.sanitized_value
        if isinstance(payload, list):
            return [
                self._sanitize_payload_recursive(item, source, f"{field_path}[{index}]", findings)
                for index, item in enumerate(payload)
            ]
        if isinstance(payload, dict):
            sanitized: dict[Any, Any] = {}
            for key, value in payload.items():
                child_path = f"{field_path}.{key}" if field_path else str(key)
                sanitized[key] = self._sanitize_payload_recursive(value, source, child_path, findings)
            return sanitized
        return payload

    @staticmethod
    def _build_findings(
        matches: list[Any],
        pattern: PromptInjectionPattern,
        field_path: str,
    ) -> list[PromptInjectionFinding]:
        findings: list[PromptInjectionFinding] = []
        for match in matches:
            matched_text = match.group(0).strip()
            findings.append(
                PromptInjectionFinding(
                    category=pattern.category,
                    pattern_id=pattern.pattern_id,
                    field_path=field_path,
                    matched_text=matched_text[:120],
                    replacement=pattern.replacement,
                )
            )
        return findings

    @staticmethod
    def _risk_level(findings: list[PromptInjectionFinding], source: str) -> str:
        if not findings:
            return "none"
        categories = {item.category for item in findings}
        if source == "restricted_config":
            return "high"
        if "injection_phrase" in categories or "role_marker" in categories:
            return "high"
        if "fake_separator" in categories or "fake_boundary_tag" in categories:
            return "medium"
        return "low"
