package com.labelhub.modules.task.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 标注员任务列表轻量响应对象。
 */
@Data
public class LabelerTaskVO {

    /**
     * 任务 ID。
     */
    private Long id;

    /**
     * 任务标题。
     */
    private String title;

    /**
     * 任务描述。
     */
    private String description;

    /**
     * 任务状态。
     */
    private String taskStatus;

    /**
     * 当前标注员已领取的任务项数量。
     */
    private Long claimedCount;

    /**
     * 当前标注员已提交或已审核通过的任务项数量。
     */
    private Long annotatedCount;

    /**
     * 当前标注员被审核驳回、待返工的任务项数量。
     */
    private Long rejectedCount;

    /**
     * 任务奖励规则。
     */
    private String rewardRule;

    /**
     * 任务截止时间。
     */
    private LocalDateTime deadline;
}
