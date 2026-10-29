package com.labelhub.modules.task.vo;

import lombok.Data;

/**
 * 任务进度统计响应对象。
 */
@Data
public class TaskProgressVO {

    private Long taskId;

    private Long totalCount;

    private Long unassignedCount;

    private Long claimedCount;

    private Long submittedCount;

    private Long rejectedCount;

    private Long reviewedCount;
}
