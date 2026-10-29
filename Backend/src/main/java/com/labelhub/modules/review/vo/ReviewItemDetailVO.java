package com.labelhub.modules.review.vo;

import lombok.Data;

import java.util.Map;

/**
 * 审核项详情。
 */
@Data
public class ReviewItemDetailVO {

    private Long taskItemId;

    private Long taskId;

    private String taskTitle;

    private String taskDescription;

    private String status;

    private String displayStatus;

    private String displayStatusName;

    private Long labelerId;

    private Map<String, Object> rawData;

    private String dataType;

    private Map<String, Object> annotationData;

    private Map<String, Object> aiPreReviewResult;

    private String reviewComment;
}
