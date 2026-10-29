package com.labelhub.modules.annotation.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelhub.common.dto.agent.AgentSuggestionCreateResultDTO;
import com.labelhub.common.dto.agent.AgentSuggestionMaterialDTO;
import com.labelhub.common.dto.agent.AgentSuggestionRequestDTO;
import com.labelhub.common.dto.agent.AgentSuggestionResultDTO;
import com.labelhub.auth.util.AuthUtil;
import com.labelhub.common.exception.BusinessException;
import com.labelhub.common.exception.ErrorCode;
import com.labelhub.common.service.AgentService;
import com.labelhub.common.service.MaterialUploadService;
import com.labelhub.common.util.RedisUtil;
import com.labelhub.modules.annotation.dto.AnnotationSaveDTO;
import com.labelhub.modules.annotation.enums.AnnotationDisplayStatusEnum;
import com.labelhub.modules.annotation.mq.AIReviewMessage;
import com.labelhub.modules.annotation.mq.AIReviewProducer;
import com.labelhub.modules.annotation.service.AnnotationService;
import com.labelhub.modules.annotation.vo.AnnotationInitVO;
import com.labelhub.modules.annotation.vo.AnnotationMaterialVO;
import com.labelhub.modules.annotation.vo.AnnotationProgressVO;
import com.labelhub.modules.annotation.vo.AnnotationSuggestionVO;
import com.labelhub.modules.annotation.vo.AnnotationTaskVO;
import com.labelhub.modules.annotation.vo.ItemDetailVO;
import com.labelhub.modules.annotation.vo.ItemNavigationVO;
import com.labelhub.modules.dataset.entity.DatasetItem;
import com.labelhub.modules.dataset.mapper.DatasetItemMapper;
import com.labelhub.modules.task.entity.Task;
import com.labelhub.modules.task.entity.TaskAIReviewConfig;
import com.labelhub.modules.task.entity.TaskItem;
import com.labelhub.modules.task.entity.TaskItemFlowRecord;
import com.labelhub.modules.task.entity.TaskProgress;
import com.labelhub.modules.task.enums.TaskItemStatusEnum;
import com.labelhub.modules.task.enums.TaskStatusEnum;
import com.labelhub.modules.task.mapper.TaskAIReviewConfigMapper;
import com.labelhub.modules.task.mapper.TaskItemFlowRecordMapper;
import com.labelhub.modules.task.mapper.TaskItemMapper;
import com.labelhub.modules.task.mapper.TaskMapper;
import com.labelhub.modules.task.mapper.TaskProgressMapper;
import com.labelhub.modules.template.entity.Material;
import com.labelhub.modules.template.handler.MaterialHandler;
import com.labelhub.modules.template.handler.MaterialHandlerFactory;
import com.labelhub.modules.template.mapper.MaterialMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.labelhub.common.constant.RedisKeyConstant.AI_REVIEW_CONTEXT_CACHE_KEY_PREFIX;
import static com.labelhub.common.constant.RedisKeyConstant.AI_REVIEW_CONTEXT_CACHE_TIMEOUT;
import static com.labelhub.common.constant.RedisKeyConstant.MATERIAL_CACHE_KEY_PREFIX;
import static com.labelhub.common.constant.RedisKeyConstant.MATERIAL_CACHE_TIMEOUT;

/**
 * 标注工作台服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnnotationServiceImpl implements AnnotationService {

    private static final String SHOW_ITEM_TYPE = "showItem";
    private static final String LLM_INTERACTION_TYPE = "llmInteraction";
    private static final String IMAGE_UPLOAD_TYPE = "imageUpload";
    private static final String FILE_UPLOAD_TYPE = "fileUpload";
    private static final String FLOW_ACTION_SUBMIT = "SUBMIT";

    private final TaskMapper taskMapper;
    private final TaskItemMapper taskItemMapper;
    private final TaskProgressMapper taskProgressMapper;
    private final DatasetItemMapper datasetItemMapper;
    private final MaterialMapper materialMapper;
    private final MaterialHandlerFactory materialHandlerFactory;
    private final ObjectMapper objectMapper;
    private final RedisUtil redisUtil;
    private final TaskItemFlowRecordMapper taskItemFlowRecordMapper;
    private final AIReviewProducer aiReviewProducer;
    private final TaskAIReviewConfigMapper taskAIReviewConfigMapper;
    private final MaterialUploadService materialUploadService;
    private final AgentService agentService;

    @Override
    public AnnotationInitVO initWorkspace(Long taskId) {
        log.info("初始化标注工作台开始：taskId={}", taskId);
        try {
            Task task = getRequiredTask(taskId);
            assertTaskAllowsAnnotationWorkspace(task);
            ensureTaskHasCurrentLabelerItems(taskId);

            AnnotationInitVO vo = new AnnotationInitVO();
            vo.setTask(toTaskVO(task));
            vo.setMaterials(listMaterials(task.getTemplateId()));
            vo.setNavigation(listNavigation(task));
            vo.setProgress(getProgress(taskId));
            log.info("初始化标注工作台成功：taskId={}", taskId);
            return vo;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("初始化标注工作台异常：{}", e.getMessage(), e);
            throw new BusinessException("初始化标注工作台失败：" + e.getMessage());
        }
    }

    /**
     * 查询题目导航。
     */
    private List<ItemNavigationVO> listNavigation(Task task) {
        Long taskId = task.getId();
        log.info("查询题目导航开始：taskId={}", taskId);
        try {
            List<TaskItem> items = listCurrentUserTaskItems(task);
            if (items.isEmpty()) {
                throw new BusinessException("当前用户未领取该任务的数据项");
            }
            List<ItemNavigationVO> navigation = new java.util.ArrayList<>();
            for (int i = 0; i < items.size(); i++) {
                TaskItem item = items.get(i);
                ItemNavigationVO vo = new ItemNavigationVO();
                vo.setIndex(i + 1);
                vo.setTaskItemId(item.getId());
                vo.setStatus(item.getStatus());
                vo.setDisplayStatus(resolveDisplayStatus(item));
                navigation.add(vo);
            }
            log.info("查询题目导航成功：taskId={}，count={}", taskId, navigation.size());
            return navigation;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("查询题目导航异常：{}", e.getMessage(), e);
            throw new BusinessException("查询题目导航失败：" + e.getMessage());
        }
    }

    /**
     * 查询标注工作台进度。
     */
    private AnnotationProgressVO getProgress(Long taskId) {
        log.info("查询标注工作台进度开始：taskId={}", taskId);
        try {
            getRequiredTask(taskId);
            Long labelerId = AuthUtil.getCurrentUserId();
            List<Map<String, Object>> rows = taskItemMapper.selectLabelerDisplayStatusCounts(taskId, labelerId);
            if (rows.isEmpty()) {
                throw new BusinessException("当前用户未领取该任务的数据项");
            }

            AnnotationProgressVO vo = new AnnotationProgressVO();
            vo.setTotal(0L);
            vo.setSubmitted(0L);
            vo.setDraft(0L);
            vo.setPending(0L);
            vo.setRejected(0L);
            vo.setReviewed(0L);
            for (Map<String, Object> row : rows) {
                String displayStatus = String.valueOf(row.get("display_status"));
                long count = ((Number) row.get("count")).longValue();
                vo.setTotal(vo.getTotal() + count);
                if (AnnotationDisplayStatusEnum.PENDING.getCode().equals(displayStatus)) {
                    vo.setPending(count);
                } else if (AnnotationDisplayStatusEnum.DRAFT.getCode().equals(displayStatus)) {
                    vo.setDraft(count);
                } else if (AnnotationDisplayStatusEnum.SUBMITTED.getCode().equals(displayStatus)) {
                    vo.setSubmitted(count);
                } else if (AnnotationDisplayStatusEnum.REJECTED.getCode().equals(displayStatus)) {
                    vo.setRejected(count);
                } else if (AnnotationDisplayStatusEnum.REVIEWED.getCode().equals(displayStatus)) {
                    vo.setReviewed(count);
                }
            }
            log.info("查询标注工作台进度成功：taskId={}", taskId);
            return vo;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("查询标注工作台进度异常：{}", e.getMessage(), e);
            throw new BusinessException("查询标注工作台进度失败：" + e.getMessage());
        }
    }

    @Override
    public ItemDetailVO getItemDetail(Long taskItemId) {
        log.info("查询标注题目详情开始：taskItemId={}", taskItemId);
        try {
            TaskItem taskItem = getRequiredOwnedTaskItem(taskItemId);
            DatasetItem datasetItem = datasetItemMapper.selectById(taskItem.getDatasetItemId());
            if (datasetItem == null) {
                throw new BusinessException("数据项不存在");
            }

            ItemDetailVO vo = new ItemDetailVO();
            vo.setTaskItemId(taskItem.getId());
            vo.setTaskId(taskItem.getTaskId());
            vo.setStatus(taskItem.getStatus());
            vo.setDisplayStatus(resolveDisplayStatus(taskItem));
            vo.setReviewComment(taskItem.getReviewComment());
            vo.setRawData(parseJsonMap(datasetItem.getContentJson()));
            vo.setDataType(datasetItem.getDataType());
            vo.setAnnotationData(parseJsonMap(taskItem.getAnnotationData()));
            log.info("查询标注题目详情成功：taskItemId={}", taskItemId);
            return vo;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("查询标注题目详情异常：{}", e.getMessage(), e);
            throw new BusinessException("查询标注题目详情失败：" + e.getMessage());
        }
    }

    @Override
    public AnnotationSuggestionVO createAiSuggestion(Long taskItemId) {
        log.info("创建AI辅助标注建议开始：taskItemId={}", taskItemId);
        try {
            TaskItem taskItem = getRequiredOwnedTaskItem(taskItemId);
            Task task = getRequiredTask(taskItem.getTaskId());
            assertTaskAllowsAnnotationWorkspace(task);
            ensureAiSuggestionEnabled(task.getTemplateId());

            DatasetItem datasetItem = datasetItemMapper.selectById(taskItem.getDatasetItemId());
            if (datasetItem == null) {
                throw new BusinessException("数据项不存在");
            }

            AgentSuggestionRequestDTO request = new AgentSuggestionRequestDTO();
            request.setTaskId(String.valueOf(task.getId()));
            request.setTaskItemId(String.valueOf(taskItem.getId()));
            request.setTaskTitle(task.getTitle());
            request.setTaskDescription(task.getDescription());
            request.setRawData(parseJsonMap(datasetItem.getContentJson()));
            request.setMaterials(buildSuggestionMaterials(task.getTemplateId()));

            AgentSuggestionCreateResultDTO result = agentService.createAnnotationSuggestion(request);
            AnnotationSuggestionVO vo = toSuggestionVO(taskItem, result);
            log.info("创建AI辅助标注建议完成：taskItemId={}，suggestionId={}，status={}",
                    taskItemId, vo.getSuggestionId(), vo.getStatus());
            return vo;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("创建AI辅助标注建议异常：{}", e.getMessage(), e);
            throw new BusinessException("创建AI辅助标注建议失败：" + e.getMessage());
        }
    }

    @Override
    public AnnotationSuggestionVO queryAiSuggestion(Long taskItemId, String suggestionId) {
        log.info("查询AI辅助标注建议开始：taskItemId={}，suggestionId={}", taskItemId, suggestionId);
        try {
            if (!StringUtils.hasText(suggestionId)) {
                throw new BusinessException("suggestionId不能为空");
            }

            TaskItem taskItem = getRequiredOwnedTaskItem(taskItemId);
            Task task = getRequiredTask(taskItem.getTaskId());
            AgentSuggestionResultDTO result = agentService.queryAnnotationSuggestion(taskItem.getId(), suggestionId);
            AnnotationSuggestionVO vo = toSuggestionVO(taskItem, result);
            vo.setAnnotationData(filterSuggestionAnnotationData(task.getTemplateId(), vo.getAnnotationData()));
            log.info("查询AI辅助标注建议完成：taskItemId={}，suggestionId={}，status={}",
                    taskItemId, suggestionId, vo.getStatus());
            return vo;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("查询AI辅助标注建议异常：{}", e.getMessage(), e);
            throw new BusinessException("查询AI辅助标注建议失败：" + e.getMessage());
        }
    }

    @Override
    @Transactional
    public void saveDraft(Long taskItemId, AnnotationSaveDTO dto) {
        saveDraft(taskItemId, dto, Map.of());
    }

    @Override
    @Transactional
    public void saveDraft(Long taskItemId, AnnotationSaveDTO dto, Map<String, MultipartFile> componentFiles) {
        log.info("保存标注草稿开始：taskItemId={}", taskItemId);
        try {
            TaskItem taskItem = getRequiredOwnedTaskItem(taskItemId);
            Task task = getRequiredTask(taskItem.getTaskId());
            assertEditable(task, taskItem);
            List<Material> materials = getCachedMaterials(task.getTemplateId());
            Map<String, Object> draftData = mergeExistingUploadValues(
                    materials, taskItem.getAnnotationData(), dto.getAnnotationData(), componentFiles);
            Map<String, Object> annotationData = materialUploadService.fillSubmissionUploadUrls(
                    materials, draftData, componentFiles);

            TaskItem update = new TaskItem();
            update.setId(taskItemId);
            update.setAnnotationData(toJson(annotationData));
            update.setStatus(TaskItemStatusEnum.DRAFT.getCode());
            taskItemMapper.updateById(update);
            updateProgressOnSaveDraft(taskItem);
            log.info("保存标注草稿成功：taskItemId={}", taskItemId);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("保存标注草稿异常：{}", e.getMessage(), e);
            throw new BusinessException("保存标注草稿失败：" + e.getMessage());
        }
    }

    @Override
    @Transactional
    public void submit(Long taskItemId, AnnotationSaveDTO dto) {
        submit(taskItemId, dto, Map.of());
    }

    @Override
    @Transactional
    public void submit(Long taskItemId, AnnotationSaveDTO dto, Map<String, MultipartFile> componentFiles) {
        log.info("提交标注开始：taskItemId={}", taskItemId);
        try {
            TaskItem taskItem = getRequiredOwnedTaskItem(taskItemId);
            Task task = getRequiredTask(taskItem.getTaskId());
            assertEditable(task, taskItem);
            List<Material> materials = getCachedMaterials(task.getTemplateId());
            Map<String, Object> submissionData = mergeExistingUploadValues(
                    materials, taskItem.getAnnotationData(), dto.getAnnotationData(), componentFiles);
            Map<String, Object> annotationData = materialUploadService.fillSubmissionUploadUrls(
                    materials, submissionData, componentFiles);
            validateRequiredMaterials(materials, annotationData);

            TaskItem update = new TaskItem();
            update.setId(taskItemId);
            update.setAnnotationData(toJson(annotationData));
            update.setStatus(TaskItemStatusEnum.REVIEWING.getCode());
            update.setCurrentReviewOrder(1);
            update.setReviewerId(null);
            update.setReviewComment(null);
            taskItemMapper.updateById(update);
            saveFlowRecord(taskItemId, AuthUtil.getCurrentUserId().toString(), FLOW_ACTION_SUBMIT,
                    annotationData, null);
            updateProgressOnSubmit(taskItem);
            tryMoveTaskToReviewing(taskItem.getTaskId());
            trySendAIReviewAfterCommit(taskItem.getTaskId(), taskItemId, annotationData);
            log.info("提交标注成功：taskItemId={}", taskItemId);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("提交标注异常：{}", e.getMessage(), e);
            throw new BusinessException("提交标注失败：" + e.getMessage());
        }
    }

    /**
     * 获取必需的任务。
     */
    private Task getRequiredTask(Long taskId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND);
        }
        return task;
    }

    /**
     * 获取当前用户拥有的任务项。
     */
    private TaskItem getRequiredOwnedTaskItem(Long taskItemId) {
        TaskItem taskItem = taskItemMapper.selectById(taskItemId);
        if (taskItem == null) {
            throw new BusinessException("任务数据项不存在");
        }
        Long currentUserId = AuthUtil.getCurrentUserId();
        if (!currentUserId.equals(taskItem.getLabelerId())) {
            throw new BusinessException("无权访问该任务数据项");
        }
        return taskItem;
    }

    /**
     * 确保当前任务存在当前标注员的任务项。
     */
    private void ensureTaskHasCurrentLabelerItems(Long taskId) {
        Long count = taskItemMapper.selectCount(new LambdaQueryWrapper<TaskItem>()
                .eq(TaskItem::getTaskId, taskId)
                .eq(TaskItem::getLabelerId, AuthUtil.getCurrentUserId()));
        if (count == null || count == 0) {
            throw new BusinessException("当前用户未领取该任务的数据项");
        }
    }

    /**
     * 查询当前用户任务项列表。
     */
    private List<TaskItem> listCurrentUserTaskItems(Task task) {
        List<String> statuses = buildNavigationStatuses(task.getStatus());
        return taskItemMapper.selectList(new LambdaQueryWrapper<TaskItem>()
                .select(TaskItem::getId, TaskItem::getStatus)
                .eq(TaskItem::getTaskId, task.getId())
                .eq(TaskItem::getLabelerId, AuthUtil.getCurrentUserId())
                .in(!statuses.isEmpty(), TaskItem::getStatus, statuses)
                .orderByAsc(TaskItem::getId));
    }

    /**
     * 按任务阶段限定标注工作台导航状态，审核中阶段显式包含被驳回的返工项。
     */
    private List<String> buildNavigationStatuses(String taskStatus) {
        if (TaskStatusEnum.REVIEWING.getCode().equals(taskStatus)) {
            return List.of(
                    TaskItemStatusEnum.REJECTED.getCode(),
                    TaskItemStatusEnum.DRAFT.getCode(),
                    TaskItemStatusEnum.SUBMITTED.getCode(),
                    TaskItemStatusEnum.REVIEWING.getCode(),
                    TaskItemStatusEnum.REVIEWED.getCode()
            );
        }
        if (TaskStatusEnum.PUBLISHED.getCode().equals(taskStatus)) {
            return List.of(
                    TaskItemStatusEnum.CLAIMED.getCode(),
                    TaskItemStatusEnum.DRAFT.getCode(),
                    TaskItemStatusEnum.SUBMITTED.getCode(),
                    TaskItemStatusEnum.REVIEWING.getCode()
            );
        }
        return List.of();
    }

    /**
     * 转换任务基础信息。
     */
    private AnnotationTaskVO toTaskVO(Task task) {
        AnnotationTaskVO vo = new AnnotationTaskVO();
        vo.setId(task.getId());
        vo.setTitle(task.getTitle());
        vo.setDescription(task.getDescription());
        vo.setDeadline(task.getDeadline());
        vo.setTemplateId(task.getTemplateId());
        vo.setDatasetId(task.getDatasetId());
        return vo;
    }

    /**
     * 查询模板物料并转换为返回对象。
     */
    private List<AnnotationMaterialVO> listMaterials(Long templateId) {
        return getCachedMaterials(templateId).stream()
                .map(this::toMaterialVO)
                .toList();
    }

    /**
     * 读取模板物料缓存。
     */
    private List<Material> getCachedMaterials(Long templateId) {
        String cacheKey = MATERIAL_CACHE_KEY_PREFIX + templateId;
        String cached = redisUtil.get(cacheKey);
        if (StringUtils.hasText(cached)) {
            try {
                return objectMapper.readValue(cached, new TypeReference<List<Material>>() {
                });
            } catch (Exception e) {
                log.warn("模板物料缓存解析失败，将重新查询数据库：templateId={}，原因={}", templateId, e.getMessage());
                redisUtil.delete(cacheKey);
            }
        }

        List<Material> materials = materialMapper.selectByTemplateId(templateId);
        try {
            redisUtil.set(cacheKey, objectMapper.writeValueAsString(materials),
                    MATERIAL_CACHE_TIMEOUT, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("模板物料写入缓存失败：templateId={}，原因={}", templateId, e.getMessage());
        }
        return materials;
    }

    /**
     * 转换物料信息。
     */
    private AnnotationMaterialVO toMaterialVO(Material material) {
        AnnotationMaterialVO vo = new AnnotationMaterialVO();
        vo.setId(material.getId());
        vo.setType(material.getType());
        vo.setField(material.getField());
        vo.setLabel(material.getLabel());
        vo.setRequired(material.getRequired() != null && material.getRequired() == 1);
        vo.setSortOrder(material.getSortOrder());
        vo.setConfig(parseJsonMap(material.getConfig()));
        return vo;
    }

    /**
     * 构建发送给 Agent 的 AI 辅助标注物料，跳过开关、图片和文件上传物料。
     */
    private List<AgentSuggestionMaterialDTO> buildSuggestionMaterials(Long templateId) {
        return getCachedMaterials(templateId).stream()
                .filter(material -> !shouldSkipAiSuggestionMaterial(material))
                .map(this::toSuggestionMaterialDTO)
                .toList();
    }

    /**
     * 转换 AI 辅助标注物料信息。
     */
    private AgentSuggestionMaterialDTO toSuggestionMaterialDTO(Material material) {
        AgentSuggestionMaterialDTO dto = new AgentSuggestionMaterialDTO();
        dto.setField(material.getField());
        dto.setLabel(material.getLabel());
        dto.setRequired(material.getRequired());
        dto.setConfig(parseJsonMap(material.getConfig()));
        return dto;
    }

    /**
     * 判断当前物料是否应跳过 AI 辅助标注建议。
     */
    private boolean shouldSkipAiSuggestionMaterial(Material material) {
        return LLM_INTERACTION_TYPE.equals(material.getType())
                || IMAGE_UPLOAD_TYPE.equals(material.getType())
                || FILE_UPLOAD_TYPE.equals(material.getType());
    }

    /**
     * 保留草稿中已上传的图片/文件 URL；如果本次重新上传同字段文件，则由新文件覆盖。
     */
    private Map<String, Object> mergeExistingUploadValues(List<Material> materials,
                                                          String existingAnnotationData,
                                                          Map<String, Object> annotationData,
                                                          Map<String, MultipartFile> componentFiles) {
        Map<String, Object> merged = annotationData == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(annotationData);
        Map<String, Object> existingData = parseJsonMap(existingAnnotationData);
        if (existingData.isEmpty()) {
            return merged;
        }

        Set<String> replacedFields = componentFiles == null ? Set.of() : componentFiles.keySet();
        materials.stream()
                .filter(this::isUploadMaterial)
                .map(Material::getField)
                .filter(StringUtils::hasText)
                .filter(field -> !replacedFields.contains(field))
                .forEach(field -> {
                    Object existingValue = existingData.get(field);
                    if (existingValue != null && !merged.containsKey(field)) {
                        merged.put(field, existingValue);
                    }
                });

        return merged;
    }

    /**
     * 判断当前物料是否为图片或文件上传物料。
     */
    private boolean isUploadMaterial(Material material) {
        return IMAGE_UPLOAD_TYPE.equals(material.getType()) || FILE_UPLOAD_TYPE.equals(material.getType());
    }

    /**
     * 过滤 AI 建议结果中的开关、图片和文件上传字段。
     */
    private Map<String, Object> filterSuggestionAnnotationData(Long templateId, Map<String, Object> annotationData) {
        if (annotationData == null || annotationData.isEmpty()) {
            return new LinkedHashMap<>();
        }

        Set<String> skippedFields = getCachedMaterials(templateId).stream()
                .filter(this::shouldSkipAiSuggestionMaterial)
                .map(Material::getField)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());

        Map<String, Object> filtered = new LinkedHashMap<>();
        annotationData.forEach((field, value) -> {
            if (!skippedFields.contains(field)) {
                filtered.put(field, value);
            }
        });
        return filtered;
    }

    /**
     * 确认当前模板通过 LLM 交互物料启用了 AI 辅助标注。
     */
    private void ensureAiSuggestionEnabled(Long templateId) {
        if (!hasLlmInteractionMaterial(templateId)) {
            throw new BusinessException("当前模板未启用AI辅助标注");
        }
    }

    /**
     * 判断模板是否存在 LLM 交互物料。该物料仅作为 AI 辅助标注开关。
     */
    private boolean hasLlmInteractionMaterial(Long templateId) {
        return getCachedMaterials(templateId).stream()
                .anyMatch(material -> LLM_INTERACTION_TYPE.equals(material.getType()));
    }

    /**
     * 转换 AI 辅助标注创建结果。
     */
    private AnnotationSuggestionVO toSuggestionVO(TaskItem taskItem, AgentSuggestionCreateResultDTO result) {
        AnnotationSuggestionVO vo = new AnnotationSuggestionVO();
        vo.setTaskId(taskItem.getTaskId());
        vo.setTaskItemId(taskItem.getId());
        if (result != null) {
            vo.setSuggestionId(result.getSuggestionId());
            vo.setStatus(result.getStatus());
            vo.setLastError(result.getLastError());
            vo.setTaskId(parseLong(result.getTaskId(), vo.getTaskId()));
            vo.setTaskItemId(parseLong(result.getTaskItemId(), vo.getTaskItemId()));
        }
        return vo;
    }

    /**
     * 转换 AI 辅助标注查询结果。
     */
    private AnnotationSuggestionVO toSuggestionVO(TaskItem taskItem, AgentSuggestionResultDTO result) {
        AnnotationSuggestionVO vo = new AnnotationSuggestionVO();
        vo.setTaskId(taskItem.getTaskId());
        vo.setTaskItemId(taskItem.getId());
        if (result != null) {
            vo.setSuggestionId(result.getSuggestionId());
            vo.setStatus(result.getStatus());
            vo.setAnnotationData(result.getAnnotationData() == null ? new LinkedHashMap<>() : result.getAnnotationData());
            vo.setLastError(result.getLastError());
            vo.setTaskId(parseLong(result.getTaskId(), vo.getTaskId()));
            vo.setTaskItemId(parseLong(result.getTaskItemId(), vo.getTaskItemId()));
        }
        return vo;
    }

    /**
     * 安全解析 Long。
     */
    private Long parseLong(String value, Long fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * 解析标注侧展示状态。
     */
    private String resolveDisplayStatus(TaskItem item) {
        if (TaskItemStatusEnum.CLAIMED.getCode().equals(item.getStatus())) {
            return AnnotationDisplayStatusEnum.PENDING.getCode();
        }
        if (TaskItemStatusEnum.DRAFT.getCode().equals(item.getStatus())) {
            return AnnotationDisplayStatusEnum.DRAFT.getCode();
        }
        if (TaskItemStatusEnum.SUBMITTED.getCode().equals(item.getStatus())
                || TaskItemStatusEnum.REVIEWING.getCode().equals(item.getStatus())) {
            return AnnotationDisplayStatusEnum.SUBMITTED.getCode();
        }
        if (TaskItemStatusEnum.REJECTED.getCode().equals(item.getStatus())) {
            return AnnotationDisplayStatusEnum.REJECTED.getCode();
        }
        if (TaskItemStatusEnum.REVIEWED.getCode().equals(item.getStatus())) {
            return AnnotationDisplayStatusEnum.REVIEWED.getCode();
        }
        return item.getStatus();
    }

    /**
     * 校验任务状态是否允许进入标注工作台。
     */
    private void assertTaskAllowsAnnotationWorkspace(Task task) {
        if (!TaskStatusEnum.PUBLISHED.getCode().equals(task.getStatus())
                && !TaskStatusEnum.REVIEWING.getCode().equals(task.getStatus())) {
            throw new BusinessException("当前任务状态不允许进入标注工作台");
        }
    }

    /**
     * 校验任务项当前是否允许编辑。
     */
    private void assertEditable(Task task, TaskItem taskItem) {
        if (TaskStatusEnum.PUBLISHED.getCode().equals(task.getStatus())) {
            if (TaskItemStatusEnum.CLAIMED.getCode().equals(taskItem.getStatus())
                    || TaskItemStatusEnum.DRAFT.getCode().equals(taskItem.getStatus())) {
                return;
            }
        }
        if (TaskStatusEnum.REVIEWING.getCode().equals(task.getStatus())) {
            if (TaskItemStatusEnum.REJECTED.getCode().equals(taskItem.getStatus())
                    || TaskItemStatusEnum.DRAFT.getCode().equals(taskItem.getStatus())) {
                return;
            }
        }
        throw new BusinessException("当前状态不允许编辑标注");
    }

    /**
     * 按模板 ID 校验提交数据。
     */
    private void validateRequiredMaterials(Long templateId, Map<String, Object> annotationData) {
        validateRequiredMaterials(getCachedMaterials(templateId), annotationData);
    }

    /**
     * 校验提交的标注数据。
     */
    private void validateRequiredMaterials(List<Material> materials, Map<String, Object> annotationData) {
        Map<String, Object> data = annotationData == null ? Map.of() : annotationData;
        List<Material> submissionMaterials = materials.stream()
                .filter(material -> !shouldSkipSubmissionValidation(material))
                .toList();
        for (Material material : submissionMaterials) {
            Object value = data.get(material.getField());
            MaterialHandler handler = materialHandlerFactory.getHandler(material.getType());
            handler.validateSubmission(material, parseJsonMap(material.getConfig()), value);
        }
    }

    /**
     * 判断当前物料是否跳过提交校验。
     */
    private boolean shouldSkipSubmissionValidation(Material material) {
        return SHOW_ITEM_TYPE.equals(material.getType()) || LLM_INTERACTION_TYPE.equals(material.getType());
    }

    /**
     * 判断当前物料是否应跳过 AI 预审 MQ。
     */
    private boolean shouldSkipAiReviewMqMaterial(Material material) {
        return LLM_INTERACTION_TYPE.equals(material.getType());
    }

    /**
     * 过滤发送给 AI 预审 MQ 的标注数据副本，忽略 LLM 交互物料字段。
     */
    private Map<String, Object> filterAnnotationDataForAiReview(
            List<Material> materials,
            Map<String, Object> annotationData
    ) {
        if (annotationData == null || annotationData.isEmpty()) {
            return new LinkedHashMap<>();
        }

        Set<String> skippedFields = materials.stream()
                .filter(this::shouldSkipAiReviewMqMaterial)
                .map(Material::getField)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());

        Map<String, Object> filtered = new LinkedHashMap<>();
        annotationData.forEach((field, value) -> {
            if (!skippedFields.contains(field)) {
                filtered.put(field, value);
            }
        });
        return filtered;
    }

    /**
     * 过滤发送给 AI 预审 MQ 的物料副本，兼容已缓存的旧上下文。
     */
    private List<AIReviewMessage.MaterialInfo> filterMaterialsForAiReview(
            List<AIReviewMessage.MaterialInfo> messageMaterials,
            List<Material> materials
    ) {
        if (messageMaterials == null || messageMaterials.isEmpty()) {
            return List.of();
        }

        Set<String> skippedFields = materials.stream()
                .filter(this::shouldSkipAiReviewMqMaterial)
                .map(Material::getField)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());

        return messageMaterials.stream()
                .filter(material -> !skippedFields.contains(material.getField()))
                .toList();
    }

    /**
     * 解析 JSON 字符串为 Map。
     */
    private Map<String, Object> parseJsonMap(String json) {
        if (!StringUtils.hasText(json)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (Exception e) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("raw", json);
            return fallback;
        }
    }

    /**
     * 将对象序列化为 JSON。
     */
    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception e) {
            throw new BusinessException("JSON 序列化失败：" + e.getMessage());
        }
    }

    /**
     * 保存任务项流转记录。
     */
    private void saveFlowRecord(Long taskItemId, String operatorId, String operatorAction,
                                Map<String, Object> annotationData, String comment) {
        TaskItemFlowRecord record = new TaskItemFlowRecord();
        record.setTaskItemId(taskItemId);
        record.setOperatorId(operatorId);
        record.setOperatorAction(operatorAction);
        record.setOperatorOrder(taskItemFlowRecordMapper.selectNextOperatorOrder(taskItemId));
        record.setAnnotationSnapshot(toJson(annotationData));
        record.setComment(comment);
        taskItemFlowRecordMapper.insert(record);
    }

    /**
     * 更新提交后的任务进度。
     */
    private void updateProgressOnSubmit(TaskItem taskItem) {
        if (TaskItemStatusEnum.CLAIMED.getCode().equals(taskItem.getStatus())) {
            taskProgressMapper.updateCounters(taskItem.getTaskId(), 0, -1, 1, 0, 0);
        } else if (TaskItemStatusEnum.DRAFT.getCode().equals(taskItem.getStatus())) {
            taskProgressMapper.updateCounters(taskItem.getTaskId(), 0, -1, 1, 0, 0);
        } else if (TaskItemStatusEnum.REJECTED.getCode().equals(taskItem.getStatus())) {
            taskProgressMapper.updateCounters(taskItem.getTaskId(), 0, 0, 1, -1, 0);
        }
    }

    /**
     * 更新保存草稿后的任务进度。
     */
    private void updateProgressOnSaveDraft(TaskItem taskItem) {
        if (TaskItemStatusEnum.REJECTED.getCode().equals(taskItem.getStatus())) {
            taskProgressMapper.updateCounters(taskItem.getTaskId(), 0, 1, 0, -1, 0);
        }
    }

    /**
     * 当所有数据项都提交后，尝试推进任务到审核中。
     */
    private void tryMoveTaskToReviewing(Long taskId) {
        Task task = getRequiredTask(taskId);
        if (!TaskStatusEnum.PUBLISHED.getCode().equals(task.getStatus())) {
            return;
        }
        TaskProgress progress = taskProgressMapper.selectOne(new LambdaQueryWrapper<TaskProgress>()
                .eq(TaskProgress::getTaskId, taskId));
        if (progress == null || progress.getTotalCount() == null || progress.getSubmittedCount() == null) {
            return;
        }
        if (progress.getTotalCount() > 0 && progress.getSubmittedCount().equals(progress.getTotalCount())) {
            int rows = taskMapper.update(null, new LambdaUpdateWrapper<Task>()
                    .eq(Task::getId, taskId)
                    .eq(Task::getStatus, TaskStatusEnum.PUBLISHED.getCode())
                    .set(Task::getStatus, TaskStatusEnum.REVIEWING.getCode()));
            if (rows > 0) {
                log.info("任务所有数据项已提交，进入审核中：taskId={}", taskId);
            }
        }
    }

    /**
     * 事务提交后发送 AI 预审消息。
     */
    private void trySendAIReviewAfterCommit(Long taskId, Long taskItemId, Map<String, Object> annotationData) {
        try {
            AIReviewMessage context = getCachedAIReviewContext(taskId);
            if (context == null) {
                log.debug("任务未配置AI预审，跳过发送MQ：taskId={}", taskId);
                return;
            }

            TaskItem taskItem = taskItemMapper.selectById(taskItemId);
            DatasetItem datasetItem = taskItem == null ? null : datasetItemMapper.selectById(taskItem.getDatasetItemId());
            Task task = getRequiredTask(taskId);
            List<Material> materials = getCachedMaterials(task.getTemplateId());

            AIReviewMessage message = new AIReviewMessage();
            message.setTaskItemId(taskItemId);
            message.setTaskId(context.getTaskId());
            message.setTaskTitle(context.getTaskTitle());
            message.setTaskDescription(context.getTaskDescription());
            message.setReviewConfig(context.getReviewConfig());
            message.setMaterials(filterMaterialsForAiReview(context.getMaterials(), materials));
            message.setRawData(parseJsonMap(datasetItem != null ? datasetItem.getContentJson() : null));
            message.setAnnotationData(filterAnnotationDataForAiReview(materials, annotationData));

            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        aiReviewProducer.sendAIReviewMessage(message);
                    }
                });
            } else {
                aiReviewProducer.sendAIReviewMessage(message);
            }
        } catch (Exception e) {
            log.error("发送AI预审消息异常：taskItemId={}, 错误={}", taskItemId, e.getMessage(), e);
        }
    }

    /**
     * 读取 AI 预审上下文缓存。
     */
    private AIReviewMessage getCachedAIReviewContext(Long taskId) {
        String cacheKey = AI_REVIEW_CONTEXT_CACHE_KEY_PREFIX + taskId;
        String cached = redisUtil.get(cacheKey);
        if (StringUtils.hasText(cached)) {
            try {
                return objectMapper.readValue(cached, AIReviewMessage.class);
            } catch (Exception e) {
                log.warn("AI预审上下文缓存解析失败，将重新查询数据库：taskId={}，原因={}", taskId, e.getMessage());
                redisUtil.delete(cacheKey);
            }
        }

        AIReviewMessage context = buildAIReviewContext(taskId);
        if (context == null) {
            return null;
        }
        try {
            redisUtil.set(cacheKey, objectMapper.writeValueAsString(context),
                    AI_REVIEW_CONTEXT_CACHE_TIMEOUT, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("AI预审上下文写入缓存失败：taskId={}，原因={}", taskId, e.getMessage());
        }
        return context;
    }

    /**
     * 构建 AI 预审上下文。
     */
    private AIReviewMessage buildAIReviewContext(Long taskId) {
        TaskAIReviewConfig config = taskAIReviewConfigMapper.selectByTaskId(taskId);
        if (config == null) {
            return null;
        }

        Task task = getRequiredTask(taskId);
        List<Material> materials = getCachedMaterials(task.getTemplateId());

        AIReviewMessage.ReviewConfigInfo reviewConfigInfo = new AIReviewMessage.ReviewConfigInfo();
        reviewConfigInfo.setReviewPrompt(config.getReviewPrompt());
        reviewConfigInfo.setPassThreshold(config.getPassThreshold());
        reviewConfigInfo.setReviewThreshold(config.getReviewThreshold());
        reviewConfigInfo.setDimensions(parseDimensionInfos(config.getDimensions()));

        AIReviewMessage context = new AIReviewMessage();
        context.setTaskId(taskId);
        context.setTaskTitle(task.getTitle());
        context.setTaskDescription(task.getDescription());
        context.setReviewConfig(reviewConfigInfo);
        context.setMaterials(materials.stream()
                .filter(material -> !shouldSkipAiReviewMqMaterial(material))
                .map(m -> {
                    AIReviewMessage.MaterialInfo info = new AIReviewMessage.MaterialInfo();
                    info.setField(m.getField());
                    info.setLabel(m.getLabel());
                    info.setRequired(m.getRequired());
                    info.setConfig(parseJsonMap(m.getConfig()));
                    return info;
                }).toList());
        return context;
    }

    /**
     * 解析 AI 预审维度配置。
     */
    private List<AIReviewMessage.DimensionInfo> parseDimensionInfos(String dimensionsJson) {
        if (!StringUtils.hasText(dimensionsJson)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(dimensionsJson, new TypeReference<List<AIReviewMessage.DimensionInfo>>() {
            });
        } catch (Exception e) {
            log.warn("解析AI预审维度JSON失败：{}", e.getMessage());
            return List.of();
        }
    }
}
