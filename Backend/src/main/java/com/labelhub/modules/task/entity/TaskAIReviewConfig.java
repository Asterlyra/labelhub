package com.labelhub.modules.task.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 任务AI预审配置实体。
 */
@Data
@TableName("task_AIreview_config")
public class TaskAIReviewConfig implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联任务ID */
    private Long taskId;

    /** 审核Prompt模板 */
    private String reviewPrompt;

    /** 评分维度JSON */
    private String dimensions;

    /** 自动通过阈值 */
    private BigDecimal passThreshold;

    /** 人工复审阈值 */
    private BigDecimal reviewThreshold;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
