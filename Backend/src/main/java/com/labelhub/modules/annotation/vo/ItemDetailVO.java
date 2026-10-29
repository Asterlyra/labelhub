package com.labelhub.modules.annotation.vo;

import lombok.Data;

import java.util.Map;

/**
 * 标注工作台单题详情。
 */
@Data
public class ItemDetailVO {

    private Long taskItemId;

    private Long taskId;

    private String status;

    private String displayStatus;

    private String reviewComment;

    private Map<String, Object> rawData;

    private String dataType;

    private Map<String, Object> annotationData;
}
