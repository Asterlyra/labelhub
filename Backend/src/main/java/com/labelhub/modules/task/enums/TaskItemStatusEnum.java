package com.labelhub.modules.task.enums;

import com.labelhub.common.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 任务数据项状态枚举。
 */
@Getter
@AllArgsConstructor
public enum TaskItemStatusEnum {

    UNASSIGNED("UNASSIGNED", "未分配"),
    CLAIMED("CLAIMED", "已领取"),
    DRAFT("DRAFT", "草稿"),
    SUBMITTED("SUBMITTED", "已提交"),
    REVIEWING("REVIEWING", "审核中"),
    REJECTED("REJECTED", "已驳回"),
    REVIEWED("REVIEWED", "已审核");

    private final String code;
    private final String desc;

    /**
     * 根据状态编码解析枚举。
     */
    public static TaskItemStatusEnum fromCode(String code) {
        return Arrays.stream(values())
                .filter(item -> item.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new BusinessException("不支持的任务项状态：" + code));
    }
}
