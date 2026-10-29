package com.labelhub.modules.task.state.impl;

import com.labelhub.modules.task.entity.Task;
import com.labelhub.modules.task.enums.TaskStatusEnum;
import com.labelhub.modules.task.state.TaskStateHandler;
import org.springframework.stereotype.Component;

/**
 * 审核中状态处理器。
 */
@Component
public class ReviewingStateHandler implements TaskStateHandler {

    @Override
    public String status() {
        return TaskStatusEnum.REVIEWING.getCode();
    }

    @Override
    public boolean canEnd(Task task) {
        return true;
    }
}
