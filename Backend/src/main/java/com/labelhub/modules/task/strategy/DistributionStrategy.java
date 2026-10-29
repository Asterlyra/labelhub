package com.labelhub.modules.task.strategy;

import com.labelhub.modules.task.entity.Task;
import com.labelhub.modules.task.entity.TaskDistributionConfig;
import com.labelhub.modules.task.entity.TaskItem;

import java.util.List;

/**
 * 任务分发策略接口。
 */
public interface DistributionStrategy {

    /**
     * 策略类型。
     */
    String strategyType();

    /**
     * 校验策略配置。
     */
    void validateConfig(TaskDistributionConfig config);

    /**
     * 初始化任务分发数据项。
     */
    void initDistribution(Task task, TaskDistributionConfig config);

    /**
     * 领取任务数据项。
     */
    List<TaskItem> claimItem(Task task, TaskDistributionConfig config, Long labelerId);

    /**
     * 获取可领取数量。
     */
    long getAvailableCount(Long taskId);
}
