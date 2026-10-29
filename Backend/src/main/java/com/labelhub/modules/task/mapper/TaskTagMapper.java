package com.labelhub.modules.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labelhub.modules.task.entity.TaskTag;
import org.apache.ibatis.annotations.Mapper;

/**
 * 任务标签 Mapper。
 */
@Mapper
public interface TaskTagMapper extends BaseMapper<TaskTag> {
}
