package com.labelhub.modules.task.state;

import com.labelhub.common.exception.BusinessException;
import com.labelhub.modules.task.entity.Task;
import com.labelhub.modules.task.enums.TaskStatusEnum;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 任务状态机管理器。
 */
@Component
public class TaskStateMachine {

    private final Map<String, TaskStateHandler> handlerMap;

    public TaskStateMachine(List<TaskStateHandler> handlers) {
        this.handlerMap = handlers.stream().collect(Collectors.toMap(TaskStateHandler::status, Function.identity()));
    }

    /**
     * 校验任务是否可发布。
     */
    public void assertCanPublish(Task task) {
        if (!handler(task).canPublish(task)) {
            throw new BusinessException("当前任务状态不允许发布：" + task.getStatus());
        }
    }

    /**
     * 校验任务是否可暂停。
     */
    public void assertCanPause(Task task) {
        if (!handler(task).canPause(task)) {
            throw new BusinessException("当前任务状态不允许暂停：" + task.getStatus());
        }
    }

    /**
     * 校验任务是否可恢复。
     */
    public void assertCanResume(Task task) {
        if (!handler(task).canResume(task)) {
            throw new BusinessException("当前任务状态不允许恢复：" + task.getStatus());
        }
    }

    /**
     * 校验任务是否可结束。
     */
    public void assertCanEnd(Task task) {
        if (!handler(task).canEnd(task)) {
            throw new BusinessException("当前任务状态不允许结束：" + task.getStatus());
        }
    }

    private TaskStateHandler handler(Task task) {
        TaskStatusEnum.fromCode(task.getStatus());
        TaskStateHandler handler = handlerMap.get(task.getStatus());
        if (handler == null) {
            throw new BusinessException("未找到任务状态处理器：" + task.getStatus());
        }
        return handler;
    }
}
