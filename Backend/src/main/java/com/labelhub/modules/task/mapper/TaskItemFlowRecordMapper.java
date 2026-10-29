package com.labelhub.modules.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labelhub.modules.task.entity.TaskItemFlowRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 任务项流转记录 Mapper。
 */
@Mapper
public interface TaskItemFlowRecordMapper extends BaseMapper<TaskItemFlowRecord> {

    /**
     * 查询任务项下一次流转顺序号。
     */
    @Select("""
            SELECT COALESCE(MAX(operator_order), 0) + 1
            FROM task_item_flow_record
            WHERE task_item_id = #{taskItemId}
            """)
    Integer selectNextOperatorOrder(@Param("taskItemId") Long taskItemId);
}
