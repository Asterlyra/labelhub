package com.labelhub.modules.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labelhub.modules.task.entity.TaskReviewer;
import org.apache.ibatis.annotations.Mapper;

/**
 * 任务审核员 Mapper。
 */
@Mapper
public interface TaskReviewerMapper extends BaseMapper<TaskReviewer> {
}
