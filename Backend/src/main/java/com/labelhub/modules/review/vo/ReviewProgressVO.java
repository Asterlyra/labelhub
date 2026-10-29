package com.labelhub.modules.review.vo;

import lombok.Data;

/**
 * 审核任务进度。
 */
@Data
public class ReviewProgressVO {

    private Long taskId;

    private Long totalCount;

    private Long pendingCount;

    private Long approvedCount;

    private Long rejectedCount;
}
