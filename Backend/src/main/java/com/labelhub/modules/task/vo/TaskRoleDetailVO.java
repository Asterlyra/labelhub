package com.labelhub.modules.task.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 按当前用户角色返回的统一任务详情。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TaskRoleDetailVO {

    private Long id;

    private String title;

    private String description;

    private Integer quota;

    private LocalDateTime deadline;

    private List<String> tags;

    private TaskProgressVO progress;

    private String datasetName;

    private String templateName;

    private String status;

    private Boolean aiReviewEnabled;

    private List<Long> reviewerIds;

    private TaskDistributionConfigVO distributionConfig;

    private TaskAIReviewConfigVO aiReviewConfig;
}
