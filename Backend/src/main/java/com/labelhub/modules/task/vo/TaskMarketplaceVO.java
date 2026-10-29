package com.labelhub.modules.task.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务广场列表响应对象。
 */
@Data
public class TaskMarketplaceVO {

    private Long id;

    private String title;

    private String description;

    private String taskStatus;

    private List<String> tags;

    private Integer quota;

    private Long claimedCount;

    private Long annotatedCount;

    private Long availableCount;

    private String rewardRule;

    private LocalDateTime deadline;

    private Long datasetId;

    private Long templateId;

    private String templateName;

    private Long creatorId;

    private String creatorName;

    private LocalDateTime createTime;
}
