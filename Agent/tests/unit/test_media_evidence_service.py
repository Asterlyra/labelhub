"""测试审核多媒体证据服务。"""

from annotation_agent.app.settings import MediaProcessingSettings
from annotation_agent.application.audit.media_evidence_service import MediaEvidenceService
from annotation_agent.infrastructure.media.media_asset_discovery import MediaAssetDiscovery
from annotation_agent.infrastructure.media.media_fetcher import DownloadedMedia


def test_media_asset_discovery_prefers_explicit_fields_and_parses_markdown():
    discovery = MediaAssetDiscovery()
    raw_data = {
        "media_type": "image",
        "media_url": "https://example.com/cover.jpg",
        "content_markdown": "![alt](https://example.com/pic.png)\n<video src=\"https://example.com/demo.mp4\"></video>",
        "attachments": [
            {"thumbnail": "https://example.com/thumb.jpg"},
            {"videoUrl": "https://example.com/clip.webm"},
        ],
    }

    markdown_text, assets = discovery.discover(raw_data, {})

    assert "alt" not in markdown_text
    assert {asset.kind for asset in assets} == {"image", "video"}
    assert {asset.locator for asset in assets} >= {
        "https://example.com/cover.jpg",
        "https://example.com/pic.png",
        "https://example.com/demo.mp4",
        "https://example.com/thumb.jpg",
        "https://example.com/clip.webm",
    }


def test_media_evidence_service_generates_media_result_and_truncation_warning(tmp_path):
    class FakeFetcher:
        def validate_image(self, locator, timeout_seconds):
            assert timeout_seconds == 10
            return "image/jpeg"

        def download_video(self, locator, timeout_seconds):
            assert timeout_seconds == 30
            video_path = tmp_path / "demo.mp4"
            video_path.write_bytes(b"video")
            return DownloadedMedia(file_path=str(video_path), content_type="video/mp4")

    class FakeExtractor:
        def extract_frames(self, video_path, positions):
            assert positions == [0.1, 0.5, 0.9]
            return [
                "data:image/jpeg;base64,frame-1",
                "data:image/jpeg;base64,frame-2",
                "data:image/jpeg;base64,frame-3",
            ]

    service = MediaEvidenceService(
        settings=MediaProcessingSettings(
            download_timeout_seconds=10,
            video_timeout_seconds=30,
            max_visual_evidence=3,
        ),
        media_fetcher=FakeFetcher(),
        video_frame_extractor=FakeExtractor(),
    )

    prepared = service.prepare(
        {
            "media_type": "image",
            "media_url": "https://example.com/cover.jpg",
            "content_markdown": "<video src=\"https://example.com/demo.mp4\"></video>",
        }
    )

    media_result = prepared.to_media_result()
    assert media_result is not None
    assert media_result["detected"] is True
    assert media_result["degraded"] is True
    assert len(media_result["assets"]) == 2
    assert len(media_result["evidenceUsed"]) == 3
    assert media_result["mediaWarnings"][0]["code"] == "MEDIA_EVIDENCE_TRUNCATED"
