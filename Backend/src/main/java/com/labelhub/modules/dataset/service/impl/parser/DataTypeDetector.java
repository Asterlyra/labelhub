package com.labelhub.modules.dataset.service.impl.parser;

import com.labelhub.modules.dataset.enums.DataTypeEnum;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Map;

/**
 * 根据内容字段粗略识别数据项类型。
 */
public final class DataTypeDetector {

    private DataTypeDetector() {
    }

    /**
     * 识别内容对应的数据类型。
     */
    public static String detect(Map<String, Object> content) {
        boolean hasText = false;
        boolean hasImage = false;
        boolean hasAudio = false;
        boolean hasVideo = false;

        for (Map.Entry<String, Object> entry : content.entrySet()) {
            String key = entry.getKey() == null ? "" : entry.getKey().toLowerCase(Locale.ROOT);
            String value = entry.getValue() == null ? "" : String.valueOf(entry.getValue()).toLowerCase(Locale.ROOT);
            hasImage = hasImage || key.contains("image") || key.contains("img") || hasMediaSuffix(value, ".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp");
            hasAudio = hasAudio || key.contains("audio") || hasMediaSuffix(value, ".mp3", ".wav", ".aac", ".flac", ".m4a");
            hasVideo = hasVideo || key.contains("video") || hasMediaSuffix(value, ".mp4", ".mov", ".avi", ".mkv", ".webm");
            hasText = hasText || key.contains("text") || key.contains("content") || key.contains("prompt")
                    || (entry.getValue() instanceof String text && StringUtils.hasText(text) && !looksLikeUrl(text));
        }

        int mediaTypes = (hasImage ? 1 : 0) + (hasAudio ? 1 : 0) + (hasVideo ? 1 : 0);
        if (mediaTypes > 1 || (mediaTypes == 1 && hasText && content.size() > 1)) {
            return DataTypeEnum.MIXED.getCode();
        }
        if (hasImage) {
            return DataTypeEnum.IMAGE.getCode();
        }
        if (hasAudio) {
            return DataTypeEnum.AUDIO.getCode();
        }
        if (hasVideo) {
            return DataTypeEnum.VIDEO.getCode();
        }
        if (hasText) {
            return DataTypeEnum.TEXT.getCode();
        }
        return DataTypeEnum.JSON.getCode();
    }

    private static boolean hasMediaSuffix(String value, String... suffixes) {
        for (String suffix : suffixes) {
            if (value.endsWith(suffix) || value.contains(suffix + "?")) {
                return true;
            }
        }
        return false;
    }

    private static boolean looksLikeUrl(String value) {
        String lowerValue = value.toLowerCase(Locale.ROOT);
        return lowerValue.startsWith("http://") || lowerValue.startsWith("https://") || lowerValue.startsWith("s3://");
    }
}
