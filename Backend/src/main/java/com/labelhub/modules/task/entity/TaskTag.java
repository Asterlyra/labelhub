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
 * 任务标签实体。
 */
@Data
@TableName("task_tag")
public class TaskTag implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private String tagName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
