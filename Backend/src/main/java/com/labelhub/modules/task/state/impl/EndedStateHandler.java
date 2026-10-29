package com.labelhub.modules.task.state.impl;

import com.labelhub.modules.task.enums.TaskStatusEnum;
import com.labelhub.modules.task.state.TaskStateHandler;
import org.springframework.stereotype.Component;

/**
 * 已结束状态处理器。
 */
@Component
public class EndedStateHandler implements TaskStateHandler {

    @Override
    public String status() {
        return TaskStatusEnum.ENDED.getCode();
    }
}
