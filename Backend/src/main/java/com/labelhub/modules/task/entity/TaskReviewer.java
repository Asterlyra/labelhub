package com.labelhub.modules.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 任务审核员关联实体。
 */
@Data
@TableName("task_reviewer")
public class TaskReviewer implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private Long reviewerId;

    private Integer reviewOrder;
}
