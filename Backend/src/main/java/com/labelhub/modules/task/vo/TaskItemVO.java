package com.labelhub.modules.task.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 任务数据项响应对象。
 */
@Data
public class TaskItemVO {

    private Long id;

    private Long taskId;

    private Long datasetItemId;

    private Long labelerId;

    private String labelerName;

    private String status;

    private Map<String, Object> annotationData;

    private Long reviewerId;

    private String reviewerName;

    private String reviewComment;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
