package com.labelhub.modules.task.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务项流转时间线节点。
 */
@Data
public class FlowTimelineVO {

    private Long id;

    private Long taskItemId;

    private String operatorName;

    private String operatorAction;

    private Integer operatorOrder;

    private String comment;

    private LocalDateTime createTime;
}
