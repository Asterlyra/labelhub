"""媒体下载与基础校验。"""
from __future__ import annotations

import os
import tempfile
import urllib.parse
import urllib.request
from dataclasses import dataclass

MAX_MEDIA_BYTES = 32 * 1024 * 1024

@dataclass(frozen=True)
class DownloadedMedia:
    file_path: str
    content_type: str

class _NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise ValueError("Redirects are not permitted for media downloads")

class MediaFetcher:
    """仅访问管理员明确允许的媒体主机，并限制下载大小。"""
    def _open(self, locator: str, timeout_seconds: float):
        parsed = urllib.parse.urlsplit(locator)
        allowed = {x.strip().lower() for x in os.environ.get("LABELHUB_MEDIA_ALLOWED_HOSTS", "").split(",") if x.strip()}
        if parsed.scheme not in {"http", "https"} or not parsed.hostname or parsed.username or parsed.password or parsed.hostname.lower() not in allowed:
            raise ValueError("Media URL host is not permitted")
        opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), _NoRedirect())
        return opener.open(urllib.request.Request(locator, method="GET"), timeout=timeout_seconds)

    def validate_image(self, locator: str, timeout_seconds: float) -> str:
        with self._open(locator, timeout_seconds) as response:
            response.read(1)
            return str(response.headers.get_content_type() or "")

    def download_video(self, locator: str, timeout_seconds: float) -> DownloadedMedia:
        with self._open(locator, timeout_seconds) as response:
            content = response.read(MAX_MEDIA_BYTES + 1)
            if len(content) > MAX_MEDIA_BYTES:
                raise ValueError("Media exceeds size limit")
            suffix = os.path.splitext(urllib.parse.urlsplit(locator).path)[1][:16] or ".mp4"
            with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as temp_file:
                temp_file.write(content)
                return DownloadedMedia(file_path=temp_file.name, content_type=str(response.headers.get_content_type() or ""))
