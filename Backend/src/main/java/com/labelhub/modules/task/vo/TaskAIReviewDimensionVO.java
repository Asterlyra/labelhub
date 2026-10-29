package com.labelhub.modules.task.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * AI预审评分维度响应对象。
 */
@Data
public class TaskAIReviewDimensionVO {

    private String field;

    private BigDecimal weight;

    private BigDecimal passScore;
}
