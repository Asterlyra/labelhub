package com.labelhub.common.dto.agent;

import lombok.Data;

/**
 * Agent AI 辅助标注创建响应。
 */
@Data
public class AgentSuggestionCreateResponseDTO {

    private String code;

    private String message;

    private AgentSuggestionCreateResultDTO data;
}
