package com.labelhub.modules.task.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * AI预审配置请求体。
 */
@Data
public class TaskAIReviewConfigDTO {

    /** 审核Prompt模板 */
    private String reviewPrompt;

    /** 评分维度列表 */
    private List<TaskAIReviewDimensionDTO> dimensions;

    /** 自动通过阈值 */
    private BigDecimal passThreshold;

    /** 人工复审阈值 */
    private BigDecimal reviewThreshold;
}
