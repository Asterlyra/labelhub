package com.labelhub.modules.task.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务列表响应对象。
 */
@Data
public class TaskVO {

    private Long id;

    private String title;

    private String description;

    private Long datasetId;

    private Long templateId;

    private String status;

    private Integer quota;

    private String rewardRule;

    private LocalDateTime deadline;

    private Long creatorId;

    private Boolean aiReviewEnabled;

    private List<String> tags;

    private TaskProgressVO progress;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
