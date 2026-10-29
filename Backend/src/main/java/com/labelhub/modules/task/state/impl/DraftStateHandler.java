package com.labelhub.modules.task.state.impl;

import com.labelhub.modules.task.entity.Task;
import com.labelhub.modules.task.enums.TaskStatusEnum;
import com.labelhub.modules.task.state.TaskStateHandler;
import org.springframework.stereotype.Component;

/**
 * 草稿状态处理器。
 */
@Component
public class DraftStateHandler implements TaskStateHandler {

    @Override
    public String status() {
        return TaskStatusEnum.DRAFT.getCode();
    }

    @Override
    public boolean canPublish(Task task) {
        return true;
    }
}
