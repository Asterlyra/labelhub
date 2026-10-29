package com.labelhub.modules.review.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 我的审核任务列表项。
 */
@Data
public class ReviewTaskVO {

    /** 任务 ID，用于进入审核工作台。 */
    private Long taskId;

    /** 任务标题。 */
    private String taskTitle;

    /** 任务描述。 */
    private String taskDescription;

    /** 任务生命周期状态，例如 PUBLISHED、PAUSED、REVIEWING。 */
    private String taskStatus;

    /** 任务数据项总数。 */
    private Long totalCount;

    /** 待审核数量，对应 task_item.status = SUBMITTED。 */
    private Long pendingCount;

    /** 已通过数量，对应 task_item.status = REVIEWED。 */
    private Long approvedCount;

    /** 已驳回数量，对应 task_item.status = REJECTED。 */
    private Long rejectedCount;

    /** 任务截止时间 */
    private LocalDateTime deadline;
}
