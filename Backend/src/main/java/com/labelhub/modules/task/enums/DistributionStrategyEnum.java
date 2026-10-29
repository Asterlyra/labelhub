package com.labelhub.modules.task.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 任务分发策略枚举。
 */
@Getter
@AllArgsConstructor
public enum DistributionStrategyEnum {

    FIRST_COME_FIRST_SERVED("FIRST_COME_FIRST_SERVED", "先到先得"),
    ASSIGNMENT("ASSIGNMENT", "指派");

    private final String code;
    private final String desc;
}
