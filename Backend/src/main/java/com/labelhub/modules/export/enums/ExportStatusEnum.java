package com.labelhub.modules.export.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 导出任务状态。
 */
@Getter
@AllArgsConstructor
public enum ExportStatusEnum {

    PENDING("PENDING", "等待中"),
    PROCESSING("PROCESSING", "处理中"),
    SUCCESS("SUCCESS", "成功"),
    FAILED("FAILED", "失败");

    private final String code;
    private final String desc;
}
