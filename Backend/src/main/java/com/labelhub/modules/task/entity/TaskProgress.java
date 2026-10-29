package com.labelhub.modules.task.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 任务进度统计表实体。
 */
@Data
@TableName("task_progress")
public class TaskProgress implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private Long totalCount;

    private Long unassignedCount;

    private Long claimedCount;

    private Long submittedCount;

    private Long rejectedCount;

    private Long reviewedCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
