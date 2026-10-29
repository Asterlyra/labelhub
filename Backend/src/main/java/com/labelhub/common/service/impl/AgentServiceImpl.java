package com.labelhub.common.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelhub.common.dto.agent.AgentAuditResultDTO;
import com.labelhub.common.dto.agent.AgentAuditResultResponseDTO;
import com.labelhub.common.dto.agent.AgentSuggestionCreateResponseDTO;
import com.labelhub.common.dto.agent.AgentSuggestionCreateResultDTO;
import com.labelhub.common.dto.agent.AgentSuggestionRequestDTO;
import com.labelhub.common.dto.agent.AgentSuggestionResultDTO;
import com.labelhub.common.dto.agent.AgentSuggestionResultResponseDTO;
import com.labelhub.common.exception.BusinessException;
import com.labelhub.common.exception.ErrorCode;
import com.labelhub.common.properties.AgentProperties;
import com.labelhub.common.service.AgentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Predicate;

/**
 * Agent 通用请求服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentServiceImpl implements AgentService {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final String AI_AUDIT_RESULT_PATH_TEMPLATE = "/api/ai-audit/tasks/%s/result";
    private static final String AI_AUDIT_RESULTS_PATH_TEMPLATE = "/api/ai-audit/tasks/%s/results";
    private static final String AI_SUGGESTION_CREATE_PATH = "/api/ai-annotation/suggestions";
    private static final String AI_SUGGESTION_QUERY_PATH_TEMPLATE =
            "/api/ai-annotation/task-items/%s/suggestions/%s";
    private final ObjectMapper objectMapper;
    private final AgentProperties agentProperties;

    @Override
    public AgentAuditResultDTO queryAiAuditResult(Long taskItemId) {
        log.info("调用Agent查询AI预审结果开始：taskItemId={}", taskItemId);
        try {
            String url = buildAgentUrl(AI_AUDIT_RESULT_PATH_TEMPLATE, taskItemId);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/json")
                    .header("X-LabelHub-Agent-Token", agentProperties.getToken())
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                log.error("调用Agent查询AI预审结果失败：taskItemId={}，httpStatus={}", taskItemId, response.statusCode());
                return buildFailedResult(taskItemId, "HTTP_" + response.statusCode());
            }

            AgentAuditResultResponseDTO result = objectMapper.readValue(response.body(), AgentAuditResultResponseDTO.class);
            if (result == null || result.getData() == null) {
                log.error("调用Agent查询AI预审结果失败：taskItemId={}，响应数据为空", taskItemId);
                return buildFailedResult(taskItemId, "响应数据为空");
            }
            if (!StringUtils.hasText(result.getCode()) || !"200".equals(result.getCode())) {
                log.error("调用Agent查询AI预审结果失败：taskItemId={}，code={}，message={}",
                        taskItemId, result == null ? null : result.getCode(), result == null ? null : result.getMessage());
                return buildFailedResult(taskItemId, result == null ? "响应异常" : result.getMessage());
            }
            AgentAuditResultDTO data = result.getData();
            if (!StringUtils.hasText(data.getTaskItemId())) {
                data.setTaskItemId(String.valueOf(taskItemId));
            }
            log.info("调用Agent查询AI预审结果成功：taskItemId={}，status={}，auditStatus={}",
                    taskItemId, data.getStatus(), data.getAuditStatus());
            return data;
        } catch (Exception e) {
            log.error("调用Agent查询AI预审结果异常：taskItemId={}，原因={}", taskItemId, e.getMessage(), e);
            return buildFailedResult(taskItemId, e.getMessage());
        }
    }

    @Override
    public JsonNode queryAiAuditResultData(Long taskItemId) {
        log.info("调用Agent查询单项AI预审结果开始：taskItemId={}", taskItemId);
        String url = buildAgentUrl(AI_AUDIT_RESULT_PATH_TEMPLATE, taskItemId);
        JsonNode data = queryAgentData(url, "调用Agent查询单项AI预审结果", JsonNode::isObject);
        log.info("调用Agent查询单项AI预审结果成功：taskItemId={}", taskItemId);
        return data;
    }

    @Override
    public JsonNode queryAiAuditResultsData(Long taskId) {
        log.info("调用Agent查询任务AI预审结果列表开始：taskId={}", taskId);
        String url = buildAgentUrl(AI_AUDIT_RESULTS_PATH_TEMPLATE, taskId);
        JsonNode data = queryAgentData(url, "调用Agent查询任务AI预审结果列表", JsonNode::isArray);
        log.info("调用Agent查询任务AI预审结果列表成功：taskId={}，count={}", taskId, data.size());
        return data;
    }

    @Override
    public AgentSuggestionCreateResultDTO createAnnotationSuggestion(AgentSuggestionRequestDTO request) {
        String taskItemId = request == null ? null : request.getTaskItemId();
        log.info("调用Agent创建AI辅助标注建议开始：taskItemId={}", taskItemId);
        try {
            String body = objectMapper.writeValueAsString(request);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(buildAgentUrl(AI_SUGGESTION_CREATE_PATH)))
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/json")
                    .header("X-LabelHub-Agent-Token", agentProperties.getToken())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(httpRequest,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                log.error("调用Agent创建AI辅助标注建议失败：taskItemId={}，httpStatus={}",
                        taskItemId, response.statusCode());
                return buildFailedSuggestionCreateResult(request, "HTTP_" + response.statusCode());
            }

            AgentSuggestionCreateResponseDTO result =
                    objectMapper.readValue(response.body(), AgentSuggestionCreateResponseDTO.class);
            if (result == null || result.getData() == null) {
                log.error("调用Agent创建AI辅助标注建议失败：taskItemId={}，响应数据为空", taskItemId);
                return buildFailedSuggestionCreateResult(request, "响应数据为空");
            }
            if (!StringUtils.hasText(result.getCode()) || !"200".equals(result.getCode())) {
                log.error("调用Agent创建AI辅助标注建议失败：taskItemId={}，code={}，message={}",
                        taskItemId, result.getCode(), result.getMessage());
                return buildFailedSuggestionCreateResult(request, result.getMessage());
            }

            AgentSuggestionCreateResultDTO data = result.getData();
            fillSuggestionIdentity(data, request);
            log.info("调用Agent创建AI辅助标注建议成功：taskItemId={}，suggestionId={}，status={}",
                    taskItemId, data.getSuggestionId(), data.getStatus());
            return data;
        } catch (Exception e) {
            log.error("调用Agent创建AI辅助标注建议异常：taskItemId={}，原因={}",
                    taskItemId, e.getMessage(), e);
            return buildFailedSuggestionCreateResult(request, e.getMessage());
        }
    }

    @Override
    public AgentSuggestionResultDTO queryAnnotationSuggestion(Long taskItemId, String suggestionId) {
        log.info("调用Agent查询AI辅助标注建议开始：taskItemId={}，suggestionId={}", taskItemId, suggestionId);
        try {
            String url = buildAgentUrl(AI_SUGGESTION_QUERY_PATH_TEMPLATE, taskItemId, suggestionId);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/json")
                    .header("X-LabelHub-Agent-Token", agentProperties.getToken())
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                log.error("调用Agent查询AI辅助标注建议失败：taskItemId={}，suggestionId={}，httpStatus={}",
                        taskItemId, suggestionId, response.statusCode());
                return buildFailedSuggestionResult(taskItemId, suggestionId, "HTTP_" + response.statusCode());
            }

            AgentSuggestionResultResponseDTO result =
                    objectMapper.readValue(response.body(), AgentSuggestionResultResponseDTO.class);
            if (result == null || result.getData() == null) {
                log.error("调用Agent查询AI辅助标注建议失败：taskItemId={}，suggestionId={}，响应数据为空",
                        taskItemId, suggestionId);
                return buildFailedSuggestionResult(taskItemId, suggestionId, "响应数据为空");
            }
            if (!StringUtils.hasText(result.getCode()) || !"200".equals(result.getCode())) {
                log.error("调用Agent查询AI辅助标注建议失败：taskItemId={}，suggestionId={}，code={}，message={}",
                        taskItemId, suggestionId, result.getCode(), result.getMessage());
                return buildFailedSuggestionResult(taskItemId, suggestionId, result.getMessage());
            }

            AgentSuggestionResultDTO data = result.getData();
            if (!StringUtils.hasText(data.getTaskItemId())) {
                data.setTaskItemId(String.valueOf(taskItemId));
            }
            if (!StringUtils.hasText(data.getSuggestionId())) {
                data.setSuggestionId(suggestionId);
            }
            log.info("调用Agent查询AI辅助标注建议成功：taskItemId={}，suggestionId={}，status={}",
                    taskItemId, suggestionId, data.getStatus());
            return data;
        } catch (Exception e) {
            log.error("调用Agent查询AI辅助标注建议异常：taskItemId={}，suggestionId={}，原因={}",
                    taskItemId, suggestionId, e.getMessage(), e);
            return buildFailedSuggestionResult(taskItemId, suggestionId, e.getMessage());
        }
    }

    /**
     * 构建审核失败结果，避免外部调用失败影响审核详情接口。
     */
    private AgentAuditResultDTO buildFailedResult(Long taskItemId, String lastError) {
        AgentAuditResultDTO result = new AgentAuditResultDTO();
        result.setTaskItemId(String.valueOf(taskItemId));
        result.setStatus("failed");
        result.setSummary("审核失败");
        result.setLastError(lastError);
        return result;
    }

    /**
     * 构建 AI 辅助标注创建失败结果。
     */
    private AgentSuggestionCreateResultDTO buildFailedSuggestionCreateResult(
            AgentSuggestionRequestDTO request,
            String lastError
    ) {
        AgentSuggestionCreateResultDTO result = new AgentSuggestionCreateResultDTO();
        fillSuggestionIdentity(result, request);
        result.setStatus("failed");
        result.setLastError(lastError);
        return result;
    }

    /**
     * 构建 AI 辅助标注查询失败结果。
     */
    private AgentSuggestionResultDTO buildFailedSuggestionResult(Long taskItemId, String suggestionId, String lastError) {
        AgentSuggestionResultDTO result = new AgentSuggestionResultDTO();
        result.setTaskItemId(String.valueOf(taskItemId));
        result.setSuggestionId(suggestionId);
        result.setStatus("failed");
        result.setLastError(lastError);
        return result;
    }

    /**
     * 补齐 AI 辅助标注任务标识。
     */
    private void fillSuggestionIdentity(AgentSuggestionCreateResultDTO data, AgentSuggestionRequestDTO request) {
        if (data == null || request == null) {
            return;
        }
        if (!StringUtils.hasText(data.getTaskId())) {
            data.setTaskId(request.getTaskId());
        }
        if (!StringUtils.hasText(data.getTaskItemId())) {
            data.setTaskItemId(request.getTaskItemId());
        }
    }

    /**
     * 查询 Agent 并返回响应中的 data 原始节点。
     */
    private JsonNode queryAgentData(String url, String operation, Predicate<JsonNode> dataValidator) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/json")
                    .header("X-LabelHub-Agent-Token", agentProperties.getToken())
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                log.error("{}失败：httpStatus={}", operation, response.statusCode());
                throw new BusinessException(ErrorCode.AGENT_REQUEST_FAILED.getCode(),
                        operation + "失败：HTTP_" + response.statusCode());
            }

            JsonNode root = readAgentResponse(response.body(), operation);
            String code = root.path("code").asText();
            if (!StringUtils.hasText(code)) {
                throw new BusinessException(ErrorCode.AGENT_RESPONSE_INVALID);
            }
            if (!"200".equals(code)) {
                String message = root.path("message").asText(ErrorCode.AGENT_REQUEST_FAILED.getMessage());
                log.error("{}失败：code={}，message={}", operation, code, message);
                throw new BusinessException(ErrorCode.AGENT_REQUEST_FAILED.getCode(), message);
            }

            JsonNode data = root.get("data");
            if (data == null || data.isNull() || !dataValidator.test(data)) {
                throw new BusinessException(ErrorCode.AGENT_RESPONSE_INVALID);
            }
            return data;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("{}异常：原因={}", operation, e.getMessage(), e);
            throw new BusinessException(ErrorCode.AGENT_REQUEST_FAILED.getCode(),
                    operation + "失败：" + e.getMessage());
        }
    }

    private JsonNode readAgentResponse(String body, String operation) {
        if (!StringUtils.hasText(body)) {
            throw new BusinessException(ErrorCode.AGENT_RESPONSE_INVALID);
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root == null || !root.isObject()) {
                throw new BusinessException(ErrorCode.AGENT_RESPONSE_INVALID);
            }
            return root;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("{}响应解析失败：原因={}", operation, e.getMessage(), e);
            throw new BusinessException(ErrorCode.AGENT_RESPONSE_INVALID);
        }
    }

    private String buildAgentUrl(String pathTemplate, Object... args) {
        return agentProperties.normalizedBaseUrl() + String.format(pathTemplate, args);
    }
}
