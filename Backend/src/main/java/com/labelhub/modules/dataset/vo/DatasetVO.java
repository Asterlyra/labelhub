package com.labelhub.modules.dataset.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 数据集列表响应对象。
 */
@Data
public class DatasetVO {

    private Long id;

    private String name;

    private String description;

    private String sourceFormat;

    private String status;

    private Integer totalCount;

    private Integer successCount;

    private Integer failedCount;

    private Map<String, Object> schema;

    private Long createdBy;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
