package com.labelhub.common.dto.agent;

import lombok.Data;

import java.util.List;

/**
 * Agent AI 预审结果数据。
 */
@Data
public class AgentAuditResultDTO {

    private String auditStatus;

    private Double confidence;

    private String lastError;

    private List<AgentAuditScoreDTO> scores;

    private String status;

    private String summary;

    private String taskId;

    private String taskItemId;

    private Double weightedScore;
}
