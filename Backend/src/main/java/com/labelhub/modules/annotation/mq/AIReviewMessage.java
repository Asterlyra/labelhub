package com.labelhub.modules.annotation.mq;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * AI预审MQ消息体。
 */
@Data
public class AIReviewMessage implements Serializable {

    /** 任务数据项ID */
    private Long taskItemId;

    /** 任务ID */
    private Long taskId;

    /** 任务名称 */
    private String taskTitle;

    /** 任务描述 */
    private String taskDescription;

    /** 模板物料列表 */
    private List<MaterialInfo> materials;

    /** AI预审配置 */
    private ReviewConfigInfo reviewConfig;

    /** 原始数据 (dataset_item.content_json) */
    private Map<String, Object> rawData;

    /** 标注数据 (task_item.annotation_data) */
    private Map<String, Object> annotationData;

    /**
     * 物料精简信息。
     */
    @Data
    public static class MaterialInfo implements Serializable {

        /** 字段标识 */
        private String field;

        /** 显示标签 */
        private String label;

        /** 是否必填 */
        private Integer required;

        /** 物料专属配置 */
        private Map<String, Object> config;
    }

    /**
     * AI预审配置精简信息。
     */
    @Data
    public static class ReviewConfigInfo implements Serializable {

        /** 审核Prompt模板 */
        private String reviewPrompt;

        /** 评分维度列表 */
        private List<DimensionInfo> dimensions;

        /** 自动通过阈值 */
        private BigDecimal passThreshold;

        /** 人工复审阈值 */
        private BigDecimal reviewThreshold;
    }

    /**
     * 评分维度信息。
     */
    @Data
    public static class DimensionInfo implements Serializable {

        /** 字段标识 */
        private String field;

        /** 权重 */
        private BigDecimal weight;

        /** 通过分 */
        private BigDecimal passScore;
    }
}
