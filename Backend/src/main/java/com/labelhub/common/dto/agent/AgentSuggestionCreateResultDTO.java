package com.labelhub.common.dto.agent;

import lombok.Data;

/**
 * Agent AI 辅助标注创建结果。
 */
@Data
public class AgentSuggestionCreateResultDTO {

    private String suggestionId;

    private String taskId;

    private String taskItemId;

    private String status;

    private String lastError;
}
