package com.labelhub.common.dto.agent;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Agent AI 辅助标注物料数据。
 */
@Data
public class AgentSuggestionMaterialDTO {

    private String field;

    private String label;

    private Integer required;

    private Map<String, Object> config = new LinkedHashMap<>();
}
