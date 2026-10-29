package com.labelhub.modules.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("task_item")
public class TaskItem implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private Long datasetItemId;

    private Long labelerId;

    private String status;

    private String annotationData;

    private Integer currentReviewOrder;

    private Long reviewerId;

    private String reviewComment;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
