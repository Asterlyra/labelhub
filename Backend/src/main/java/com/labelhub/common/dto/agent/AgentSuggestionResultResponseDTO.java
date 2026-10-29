package com.labelhub.common.dto.agent;

import lombok.Data;

/**
 * Agent AI 辅助标注查询响应。
 */
@Data
public class AgentSuggestionResultResponseDTO {

    private String code;

    private String message;

    private AgentSuggestionResultDTO data;
}
