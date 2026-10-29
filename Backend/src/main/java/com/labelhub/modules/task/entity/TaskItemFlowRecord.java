package com.labelhub.modules.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 任务项流转记录实体。
 */
@Data
@TableName("task_item_flow_record")
public class TaskItemFlowRecord implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskItemId;

    private String operatorId;

    private String operatorAction;

    private Integer operatorOrder;

    private String annotationSnapshot;

    private String comment;

    private LocalDateTime createTime;
}
