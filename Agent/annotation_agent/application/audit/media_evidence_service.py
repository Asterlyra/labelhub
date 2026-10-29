"""审核多媒体证据准备服务。"""

from __future__ import annotations

from typing import Any

from annotation_agent.app.settings import MediaProcessingSettings
from annotation_agent.domain.audit.media_models import EvidenceUsedItem, MediaWarningItem, PreparedMediaEvidence
from annotation_agent.infrastructure.media.media_asset_discovery import MediaAssetDiscovery
from annotation_agent.infrastructure.media.media_fetcher import MediaFetcher
from annotation_agent.infrastructure.media.video_frame_extractor import VideoFrameExtractor


class MediaEvidenceService:
    """识别并准备审核所需的多媒体证据。"""

    def __init__(
        self,
        settings: MediaProcessingSettings,
        discovery: MediaAssetDiscovery | None = None,
        media_fetcher: MediaFetcher | None = None,
        video_frame_extractor: VideoFrameExtractor | None = None,
    ) -> None:
        self.settings = settings
        self.discovery = discovery or MediaAssetDiscovery()
        self.media_fetcher = media_fetcher or MediaFetcher()
        self.video_frame_extractor = video_frame_extractor or VideoFrameExtractor()

    def prepare(self, raw_data: Any, annotation_schema: dict[str, Any] | list[Any] | None = None) -> PreparedMediaEvidence:
        markdown_text, assets = self.discovery.discover(raw_data, annotation_schema)
        if not assets:
            return PreparedMediaEvidence()

        evidence_used: list[EvidenceUsedItem] = []
        warnings: list[MediaWarningItem] = []
        max_visual_evidence = max(0, self.settings.max_visual_evidence)
        video_frame_positions = [0.1, 0.5, 0.9]

        for asset in assets:
            if asset.kind == "image":
                if len(evidence_used) >= max_visual_evidence:
                    warnings.append(self._build_truncate_warning(asset.source_path, asset.locator))
                    continue
                try:
                    content_type = self.media_fetcher.validate_image(asset.locator, self.settings.download_timeout_seconds)
                    evidence_used.append(
                        EvidenceUsedItem(
                            kind="image",
                            source_path=asset.source_path,
                            origin=asset.origin,
                            locator=asset.locator,
                            note=f"validated image ({content_type or asset.mime_hint or 'unknown type'})",
                        )
                    )
                except Exception:
                    warnings.append(
                        MediaWarningItem(
                            code="IMAGE_FETCH_FAILED",
                            message="图片加载失败，已降级为文本审核",
                            source_path=asset.source_path,
                            locator=asset.locator,
                        )
                    )
            elif asset.kind == "video":
                if len(evidence_used) >= max_visual_evidence:
                    warnings.append(self._build_truncate_warning(asset.source_path, asset.locator))
                    continue
                try:
                    downloaded = self.media_fetcher.download_video(asset.locator, self.settings.video_timeout_seconds)
                    frame_urls = self.video_frame_extractor.extract_frames(downloaded.file_path, video_frame_positions)
                except Exception:
                    warnings.append(
                        MediaWarningItem(
                            code="VIDEO_FRAME_EXTRACT_FAILED",
                            message="视频抽帧失败，已降级为文本审核",
                            source_path=asset.source_path,
                            locator=asset.locator,
                        )
                    )
                    continue

                for frame_index, frame_url in enumerate(frame_urls, start=1):
                    if len(evidence_used) >= max_visual_evidence:
                        warnings.append(self._build_truncate_warning(asset.source_path, asset.locator))
                        break
                    evidence_used.append(
                        EvidenceUsedItem(
                            kind="video_frame",
                            source_path=asset.source_path,
                            origin=asset.origin,
                            locator=frame_url,
                            note=f"video frame {frame_index}",
                        )
                    )

        return PreparedMediaEvidence(
            detected=True,
            markdown_text=markdown_text,
            assets=assets,
            evidence_used=evidence_used,
            media_warnings=warnings,
            degraded=bool(warnings),
        )

    @staticmethod
    def _build_truncate_warning(source_path: str, locator: str) -> MediaWarningItem:
        return MediaWarningItem(
            code="MEDIA_EVIDENCE_TRUNCATED",
            message="视觉证据超过上限，已截断",
            source_path=source_path,
            locator=locator,
        )
