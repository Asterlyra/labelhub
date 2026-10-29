package com.labelhub.modules.task.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labelhub.modules.task.entity.TaskAIReviewConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 任务AI预审配置 Mapper。
 */
@Mapper
public interface TaskAIReviewConfigMapper extends BaseMapper<TaskAIReviewConfig> {

    /**
     * 根据任务ID查询AI预审配置。
     */
    default TaskAIReviewConfig selectByTaskId(Long taskId) {
        return selectOne(new LambdaQueryWrapper<TaskAIReviewConfig>().eq(TaskAIReviewConfig::getTaskId, taskId));
    }
}
