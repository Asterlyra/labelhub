package com.labelhub.modules.dataset.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 数据集生命周期状态枚举。
 */
@Getter
@AllArgsConstructor
public enum DatasetStatusEnum {

    UPLOADED("UPLOADED", "已上传"),
    PARSING("PARSING", "解析中"),
    PARSED("PARSED", "已解析"),
    FAILED("FAILED", "解析失败");

    private final String code;
    private final String desc;
}
