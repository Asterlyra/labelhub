package com.labelhub.common.dto.agent;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Agent AI 辅助标注查询结果。
 */
@Data
public class AgentSuggestionResultDTO {

    private String suggestionId;

    private String taskId;

    private String taskItemId;

    private String status;

    private Map<String, Object> annotationData = new LinkedHashMap<>();

    private String lastError;
}
