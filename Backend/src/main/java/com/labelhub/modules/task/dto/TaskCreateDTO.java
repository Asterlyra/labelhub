package com.labelhub.modules.task.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 创建任务请求体。
 */
@Data
public class TaskCreateDTO {

    @NotBlank(message = "任务标题不能为空")
    private String title;

    private String description;

    @NotNull(message = "数据集ID不能为空")
    private Long datasetId;

    @NotNull(message = "模板ID不能为空")
    private Long templateId;

    @NotNull(message = "任务配额不能为空")
    @Min(value = 0, message = "任务配额不能小于0")
    private Integer quota;

    private String rewardRule;

    private LocalDateTime deadline;

    private List<String> tags;

    @NotEmpty(message = "审核员不能为空")
    private List<Long> reviewerIds;

    private DistributionConfigDTO distributionConfig;

    private Boolean aiReviewEnabled;

    @Valid
    private TaskAIReviewConfigDTO aiReviewConfig;
}
