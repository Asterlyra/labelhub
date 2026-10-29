package com.labelhub.modules.dataset.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 数据项内容类型枚举。
 */
@Getter
@AllArgsConstructor
public enum DataTypeEnum {

    TEXT("TEXT", "文本"),
    IMAGE("IMAGE", "图片"),
    AUDIO("AUDIO", "音频"),
    VIDEO("VIDEO", "视频"),
    MIXED("MIXED", "混合"),
    JSON("JSON", "JSON");

    private final String code;
    private final String desc;
}
