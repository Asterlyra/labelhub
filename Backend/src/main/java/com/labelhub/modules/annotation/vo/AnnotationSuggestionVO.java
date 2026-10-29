package com.labelhub.modules.annotation.vo;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 标注工作台 AI 辅助标注建议。
 */
@Data
public class AnnotationSuggestionVO {

    private Long taskId;

    private Long taskItemId;

    private String suggestionId;

    private String status;

    private Map<String, Object> annotationData = new LinkedHashMap<>();

    private String lastError;
}
