package com.labelhub.modules.task.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 任务详情响应对象。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TaskDetailVO extends TaskVO {

    private List<Long> reviewerIds;

    private TaskDistributionConfigVO distributionConfig;

    private TaskAIReviewConfigVO aiReviewConfig;
}
