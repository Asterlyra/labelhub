"""审核多媒体证据值对象。"""

from __future__ import annotations

from dataclasses import dataclass, field


@dataclass(frozen=True)
class MediaAsset:
    """识别出的媒体资产。"""

    asset_id: str
    kind: str
    source_path: str
    origin: str
    locator: str
    mime_hint: str = ""

    def to_dict(self) -> dict[str, str]:
        return {
            "assetId": self.asset_id,
            "kind": self.kind,
            "sourcePath": self.source_path,
            "origin": self.origin,
            "locator": self.locator,
            "mimeHint": self.mime_hint,
        }


@dataclass(frozen=True)
class EvidenceUsedItem:
    """实际送入模型的媒体证据。"""

    kind: str
    source_path: str
    origin: str
    locator: str
    note: str = ""

    def to_dict(self) -> dict[str, str]:
        return {
            "kind": self.kind,
            "sourcePath": self.source_path,
            "origin": self.origin,
            "locator": self.locator,
            "note": self.note,
        }


@dataclass(frozen=True)
class MediaWarningItem:
    """媒体处理过程中的告警。"""

    code: str
    message: str
    source_path: str
    locator: str = ""

    def to_dict(self) -> dict[str, str]:
        return {
            "code": self.code,
            "message": self.message,
            "sourcePath": self.source_path,
            "locator": self.locator,
        }


@dataclass(frozen=True)
class PreparedMediaEvidence:
    """供审核流程消费的媒体证据包。"""

    detected: bool = False
    markdown_text: str = ""
    assets: list[MediaAsset] = field(default_factory=list)
    evidence_used: list[EvidenceUsedItem] = field(default_factory=list)
    media_warnings: list[MediaWarningItem] = field(default_factory=list)
    degraded: bool = False

    def to_prompt_section(self) -> str:
        """渲染媒体摘要，拼到用户提示词中。"""
        if not self.detected:
            return ""

        asset_lines = [
            f"- {asset.kind} @ {asset.source_path}: {asset.locator}"
            for asset in self.assets
        ]
        evidence_lines = [
            f"- {item.kind} from {item.source_path}: {item.note or item.locator}"
            for item in self.evidence_used
        ]
        warning_lines = [
            f"- {warning.code}: {warning.message}"
            for warning in self.media_warnings
        ]
        sections = ["检测到多媒体输入。"]
        if self.markdown_text:
            sections.append(f"Markdown正文摘要：{self.markdown_text}")
        if asset_lines:
            sections.append("媒体资产：\n" + "\n".join(asset_lines))
        if evidence_lines:
            sections.append("已使用的媒体证据：\n" + "\n".join(evidence_lines))
        if warning_lines:
            sections.append("媒体处理告警：\n" + "\n".join(warning_lines))
        return "\n\n".join(sections)

    def to_media_result(self) -> dict[str, object] | None:
        """转换为外部返回的 mediaResult 结构。"""
        if not self.detected:
            return None
        return {
            "detected": True,
            "degraded": self.degraded,
            "assets": [asset.to_dict() for asset in self.assets],
            "evidenceUsed": [item.to_dict() for item in self.evidence_used],
            "mediaWarnings": [warning.to_dict() for warning in self.media_warnings],
        }
