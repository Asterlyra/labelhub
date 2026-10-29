package com.labelhub.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum TaskStatusEnum {

    PENDING("PENDING", "待分配"),
    ASSIGNED("ASSIGNED", "已分配"),
    IN_PROGRESS("IN_PROGRESS", "进行中"),
    SUBMITTED("SUBMITTED", "已提交"),
    REVIEWING("REVIEWING", "审核中"),
    APPROVED("APPROVED", "已通过"),
    REJECTED("REJECTED", "已驳回");

    @EnumValue
    private final String code;

    @JsonValue
    private final String desc;
}
