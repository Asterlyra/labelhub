package com.labelhub.modules.task.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.labelhub.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("task")
public class Task extends BaseEntity {

    /** 任务标题 */
    private String title;

    /** 任务描述 */
    private String description;

    /** 绑定的数据集ID */
    private Long datasetId;

    /** 绑定的模板ID */
    private Long templateId;

    /** 任务状态 */
    private String status;

    /** 任务需要完成的标注总数，0表示不限 */
    private Integer quota;

    /** 奖励规则说明 */
    private String rewardRule;

    /** 截止时间 */
    private LocalDateTime deadline;

    /** 创建人ID */
    private Long creatorId;

    /** 是否启用AI预审 */
    private Boolean aiReviewEnabled;
}
