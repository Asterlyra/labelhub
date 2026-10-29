package com.labelhub.modules.dataset.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 数据项响应对象。
 */
@Data
public class DatasetItemVO {

    private Long id;

    private Long datasetId;

    private String itemKey;

    private Integer rowIndex;

    private String dataType;

    private Map<String, Object> content;

    private String parseStatus;

    private String errorMessage;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
