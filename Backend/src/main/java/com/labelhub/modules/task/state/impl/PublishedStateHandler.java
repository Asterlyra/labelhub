package com.labelhub.modules.task.state.impl;

import com.labelhub.modules.task.entity.Task;
import com.labelhub.modules.task.enums.TaskStatusEnum;
import com.labelhub.modules.task.state.TaskStateHandler;
import org.springframework.stereotype.Component;

/**
 * 发布中状态处理器。
 */
@Component
public class PublishedStateHandler implements TaskStateHandler {

    @Override
    public String status() {
        return TaskStatusEnum.PUBLISHED.getCode();
    }

    @Override
    public boolean canPause(Task task) {
        return true;
    }

    @Override
    public boolean canEnd(Task task) {
        return true;
    }

    @Override
    public boolean canCancelPublish(Task task) {
        return true;
    }
}
