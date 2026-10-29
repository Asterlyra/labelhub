package com.labelhub.modules.task.enums;

import com.labelhub.common.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 任务生命周期状态枚举。
 */
@Getter
@AllArgsConstructor
public enum TaskStatusEnum {

    DRAFT("DRAFT", "草稿"),
    PUBLISHED("PUBLISHED", "发布中"),
    PAUSED("PAUSED", "已暂停"),
    REVIEWING("REVIEWING", "审核中"),
    ENDED("ENDED", "已结束");

    private final String code;
    private final String desc;

    /**
     * 根据状态编码解析枚举。
     */
    public static TaskStatusEnum fromCode(String code) {
        return Arrays.stream(values())
                .filter(item -> item.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new BusinessException("不支持的任务状态：" + code));
    }
}
