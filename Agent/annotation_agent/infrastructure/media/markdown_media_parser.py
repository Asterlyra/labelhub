"""Markdown 媒体解析。"""

from __future__ import annotations

import re

from annotation_agent.domain.audit.media_models import MediaAsset


_IMAGE_PATTERN = re.compile(r"!\[[^\]]*]\(([^)]+)\)")
_VIDEO_TAG_PATTERN = re.compile(r"<video[^>]*src=[\"']([^\"']+)[\"'][^>]*>", re.IGNORECASE)
_VIDEO_LINK_PATTERN = re.compile(r"\((https?://[^)]+\.(?:mp4|mov|webm|m4v)(?:\?[^)]*)?)\)", re.IGNORECASE)
_HTML_TAG_PATTERN = re.compile(r"<[^>]+>")
_HEADING_PATTERN = re.compile(r"^\s{0,3}#{1,6}\s*", re.MULTILINE)


class MarkdownMediaParser:
    """解析 markdown 中的正文和媒体链接。"""

    def parse(self, markdown_text: str, source_path: str) -> tuple[str, list[MediaAsset]]:
        if not isinstance(markdown_text, str) or not markdown_text.strip():
            return "", []

        assets: list[MediaAsset] = []
        asset_index = 0

        for locator in _IMAGE_PATTERN.findall(markdown_text):
            assets.append(
                MediaAsset(
                    asset_id=f"{source_path}#{asset_index}",
                    kind="image",
                    source_path=source_path,
                    origin="markdown",
                    locator=locator.strip(),
                )
            )
            asset_index += 1

        for locator in _VIDEO_TAG_PATTERN.findall(markdown_text):
            assets.append(
                MediaAsset(
                    asset_id=f"{source_path}#{asset_index}",
                    kind="video",
                    source_path=source_path,
                    origin="markdown",
                    locator=locator.strip(),
                )
            )
            asset_index += 1

        for locator in _VIDEO_LINK_PATTERN.findall(markdown_text):
            assets.append(
                MediaAsset(
                    asset_id=f"{source_path}#{asset_index}",
                    kind="video",
                    source_path=source_path,
                    origin="markdown",
                    locator=locator.strip(),
                )
            )
            asset_index += 1

        readable_text = _IMAGE_PATTERN.sub("", markdown_text)
        readable_text = _VIDEO_TAG_PATTERN.sub("", readable_text)
        readable_text = _HEADING_PATTERN.sub("", readable_text)
        readable_text = _HTML_TAG_PATTERN.sub("", readable_text)
        readable_text = re.sub(r"\[([^\]]+)]\(([^)]+)\)", r"\1", readable_text)
        readable_text = re.sub(r"\s+", " ", readable_text).strip()
        return readable_text[:500], assets
