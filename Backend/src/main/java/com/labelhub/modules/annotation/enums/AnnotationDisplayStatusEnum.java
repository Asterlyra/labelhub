package com.labelhub.modules.annotation.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 标注工作台展示状态。
 */
@Getter
@AllArgsConstructor
public enum AnnotationDisplayStatusEnum {

    PENDING("PENDING", "待标注"),
    DRAFT("DRAFT", "草稿"),
    SUBMITTED("SUBMITTED", "已提交"),
    REJECTED("REJECTED", "已驳回"),
    REVIEWED("REVIEWED", "已通过");

    private final String code;

    private final String desc;
}
