package com.labelhub.common.dto.agent;

import lombok.Data;

/**
 * Agent AI 预审结果响应。
 */
@Data
public class AgentAuditResultResponseDTO {

    private String code;

    private String message;

    private AgentAuditResultDTO data;
}
