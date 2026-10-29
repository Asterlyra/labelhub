package com.labelhub.modules.task.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * AI预审配置响应对象。
 */
@Data
public class TaskAIReviewConfigVO {

    private Long id;

    private Long taskId;

    private String reviewPrompt;

    private List<TaskAIReviewDimensionVO> dimensions;

    private BigDecimal passThreshold;

    private BigDecimal reviewThreshold;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
