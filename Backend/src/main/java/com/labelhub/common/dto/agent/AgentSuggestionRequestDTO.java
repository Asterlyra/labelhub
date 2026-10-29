package com.labelhub.common.dto.agent;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent AI 辅助标注创建请求。
 */
@Data
public class AgentSuggestionRequestDTO {

    private String taskId;

    private String taskItemId;

    private String taskTitle;

    private String taskDescription;

    private List<AgentSuggestionMaterialDTO> materials;

    private Map<String, Object> rawData = new LinkedHashMap<>();
}
