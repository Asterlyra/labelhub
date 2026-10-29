package com.labelhub.common.dto.agent;

import lombok.Data;

/**
 * Agent AI 预审评分维度。
 */
@Data
public class AgentAuditScoreDTO {

    private String dimension;

    private Integer passScore;

    private String reason;

    private Integer score;

    private Integer weight;
}
