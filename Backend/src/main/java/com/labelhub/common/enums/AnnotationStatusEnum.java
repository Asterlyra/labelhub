package com.labelhub.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum AnnotationStatusEnum {

    SUBMITTED("SUBMITTED", "已提交"),
    APPROVED("APPROVED", "已通过"),
    REJECTED("REJECTED", "已驳回"),
    REVISED("REVISED", "已修正");

    @EnumValue
    private final String code;

    @JsonValue
    private final String desc;
}
