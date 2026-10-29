package com.labelhub.modules.task.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * AI预审评分维度请求体。
 */
@Data
public class TaskAIReviewDimensionDTO {

    /** 对应模板物料的字段标识 */
    private String field;

    /** 评分维度权重 */
    private BigDecimal weight;

    /** 该维度通过分 */
    private BigDecimal passScore;
}
