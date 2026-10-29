package com.labelhub.common.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.labelhub.common.dto.agent.AgentAuditResultDTO;
import com.labelhub.common.dto.agent.AgentSuggestionCreateResultDTO;
import com.labelhub.common.dto.agent.AgentSuggestionRequestDTO;
import com.labelhub.common.dto.agent.AgentSuggestionResultDTO;

/**
 * Agent 通用请求服务。
 */
public interface AgentService {

    /**
     * 查询任务项 AI 预审结果。
     */
    AgentAuditResultDTO queryAiAuditResult(Long taskItemId);

    /**
     * 查询任务项 AI 预审结果，返回 Agent data 原始数据。
     */
    JsonNode queryAiAuditResultData(Long taskItemId);

    /**
     * 查询任务 AI 预审结果列表，返回 Agent data 原始数据。
     */
    JsonNode queryAiAuditResultsData(Long taskId);

    /**
     * 创建任务项 AI 辅助标注建议。
     */
    AgentSuggestionCreateResultDTO createAnnotationSuggestion(AgentSuggestionRequestDTO request);

    /**
     * 查询任务项 AI 辅助标注建议结果。
     */
    AgentSuggestionResultDTO queryAnnotationSuggestion(Long taskItemId, String suggestionId);
}
