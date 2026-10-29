package com.labelhub.modules.task.state;

import com.labelhub.modules.task.entity.Task;

/**
 * 任务状态处理器接口。
 */
public interface TaskStateHandler {

    /**
     * 当前处理器支持的状态。
     */
    String status();

    default boolean canPublish(Task task) {
        return false;
    }

    default boolean canPause(Task task) {
        return false;
    }

    default boolean canResume(Task task) {
        return false;
    }

    default boolean canEnd(Task task) {
        return false;
    }

    default boolean canCancelPublish(Task task) {
        return false;
    }

    default void onEnter(Task task) {
    }

    default void onExit(Task task) {
    }
}
