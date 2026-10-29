"""视频抽帧。"""

from __future__ import annotations

import base64
import os
from io import BytesIO


class VideoFrameExtractor:
    """从视频中抽取关键帧并编码为 data URL。"""

    def extract_frames(self, video_path: str, positions: list[float]) -> list[str]:
        try:
            import imageio.v2 as imageio
            from PIL import Image
        except ImportError as exc:  # pragma: no cover - depends on optional runtime deps
            raise RuntimeError("Missing imageio/Pillow dependencies for video processing") from exc

        reader = imageio.get_reader(video_path, "ffmpeg")
        try:
            frame_count = reader.count_frames()
            if frame_count <= 0:
                raise RuntimeError("Video contains no readable frames")
            frame_indexes = sorted({min(frame_count - 1, max(0, int(round((frame_count - 1) * position)))) for position in positions})
            encoded_frames: list[str] = []
            for frame_index in frame_indexes:
                frame = reader.get_data(frame_index)
                image = Image.fromarray(frame)
                buffer = BytesIO()
                image.save(buffer, format="JPEG")
                encoded_frames.append(
                    "data:image/jpeg;base64," + base64.b64encode(buffer.getvalue()).decode("ascii")
                )
            return encoded_frames
        finally:
            reader.close()
            if os.path.exists(video_path):
                os.unlink(video_path)
