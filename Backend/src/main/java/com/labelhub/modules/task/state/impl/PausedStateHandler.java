package com.labelhub.modules.task.state.impl;

import com.labelhub.modules.task.entity.Task;
import com.labelhub.modules.task.enums.TaskStatusEnum;
import com.labelhub.modules.task.state.TaskStateHandler;
import org.springframework.stereotype.Component;

/**
 * 已暂停状态处理器。
 */
@Component
public class PausedStateHandler implements TaskStateHandler {

    @Override
    public String status() {
        return TaskStatusEnum.PAUSED.getCode();
    }

    @Override
    public boolean canResume(Task task) {
        return true;
    }

    @Override
    public boolean canEnd(Task task) {
        return true;
    }
}
