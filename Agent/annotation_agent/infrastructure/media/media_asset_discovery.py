"""媒体资产识别。"""

from __future__ import annotations

import mimetypes
import re
from typing import Any

from annotation_agent.domain.audit.media_models import MediaAsset
from annotation_agent.infrastructure.media.markdown_media_parser import MarkdownMediaParser


_IMAGE_KEYWORDS = ("image", "img", "thumbnail", "cover")
_VIDEO_KEYWORDS = ("video", "media", "frame", "clip")
_URL_PATTERN = re.compile(r"^https?://", re.IGNORECASE)
_MARKDOWN_HINT_PATTERN = re.compile(r"!\[[^\]]*]\(|<video[^>]*src=", re.IGNORECASE)
_VIDEO_EXTENSIONS = (".mp4", ".mov", ".webm", ".m4v")
_IMAGE_EXTENSIONS = (".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp")


class MediaAssetDiscovery:
    """显式字段优先，启发式兜底的媒体识别器。"""

    def __init__(self, markdown_parser: MarkdownMediaParser | None = None) -> None:
        self.markdown_parser = markdown_parser or MarkdownMediaParser()

    def discover(self, raw_data: Any, annotation_schema: dict[str, Any] | list[Any] | None = None) -> tuple[str, list[MediaAsset]]:
        assets: list[MediaAsset] = []
        markdown_text = ""
        seen: set[str] = set()
        if isinstance(raw_data, dict):
            markdown_payload = str(raw_data.get("content_markdown") or "").strip()
            media_type = str(raw_data.get("media_type") or "").strip().lower()
            media_url = str(raw_data.get("media_url") or "").strip()
            if media_type in {"image", "video"} and media_url:
                seen.add(media_url)
                assets.append(
                    MediaAsset(
                        asset_id="rawData.media_url#0",
                        kind=media_type,
                        source_path="rawData.media_url",
                        origin="raw",
                        locator=media_url,
                        mime_hint=mimetypes.guess_type(media_url)[0] or "",
                    )
                )
            if markdown_payload:
                markdown_text, markdown_assets = self.markdown_parser.parse(markdown_payload, "rawData.content_markdown")
                for asset in markdown_assets:
                    if asset.locator in seen:
                        continue
                    assets.append(asset)
                    seen.add(asset.locator)

        assets.extend(self._discover_fallback(raw_data, path="rawData", seen=seen))
        if annotation_schema:
            assets.extend(self._discover_fallback(annotation_schema, path="annotationSchema", seen=seen))
        return markdown_text, assets

    def _discover_fallback(self, value: Any, path: str, seen: set[str]) -> list[MediaAsset]:
        assets: list[MediaAsset] = []
        if isinstance(value, dict):
            for key, item in value.items():
                child_path = f"{path}.{key}"
                lowered_key = str(key).lower()
                assets.extend(self._discover_from_value(item, child_path, lowered_key, seen))
        elif isinstance(value, list):
            for index, item in enumerate(value):
                child_path = f"{path}[{index}]"
                assets.extend(self._discover_from_value(item, child_path, "", seen))
        return assets

    def _discover_from_value(self, item: Any, path: str, lowered_key: str, seen: set[str]) -> list[MediaAsset]:
        assets: list[MediaAsset] = []
        if isinstance(item, (dict, list)):
            return self._discover_fallback(item, path, seen)
        if not isinstance(item, str):
            return assets
        candidate = item.strip()
        if not candidate:
            return assets
        if path.endswith("content_markdown") or _MARKDOWN_HINT_PATTERN.search(candidate):
            _, markdown_assets = self.markdown_parser.parse(candidate, path)
            for asset in markdown_assets:
                if asset.locator in seen:
                    continue
                assets.append(asset)
                seen.add(asset.locator)
            return assets
        if not _URL_PATTERN.match(candidate):
            return assets
        kind = self._guess_kind(candidate, lowered_key)
        if not kind or candidate in seen:
            return assets
        seen.add(candidate)
        assets.append(
            MediaAsset(
                asset_id=f"{path}#0",
                kind=kind,
                source_path=path,
                origin="raw",
                locator=candidate,
                mime_hint=mimetypes.guess_type(candidate)[0] or "",
            )
        )
        return assets

    @staticmethod
    def _guess_kind(locator: str, lowered_key: str) -> str:
        normalized = locator.lower().split("?", 1)[0]
        if any(keyword in lowered_key for keyword in _IMAGE_KEYWORDS) or normalized.endswith(_IMAGE_EXTENSIONS):
            return "image"
        if any(keyword in lowered_key for keyword in _VIDEO_KEYWORDS) or normalized.endswith(_VIDEO_EXTENSIONS):
            return "video"
        return ""
