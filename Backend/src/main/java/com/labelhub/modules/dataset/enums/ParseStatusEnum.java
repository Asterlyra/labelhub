package com.labelhub.modules.dataset.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 数据项解析结果状态枚举。
 */
@Getter
@AllArgsConstructor
public enum ParseStatusEnum {

    SUCCESS("SUCCESS", "成功"),
    FAILED("FAILED", "失败");

    private final String code;
    private final String desc;
}
