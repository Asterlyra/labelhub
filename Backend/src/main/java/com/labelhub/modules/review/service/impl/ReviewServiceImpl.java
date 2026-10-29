package com.labelhub.modules.review.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelhub.auth.util.AuthUtil;
import com.labelhub.common.dto.agent.AgentAuditResultDTO;
import com.labelhub.common.exception.BusinessException;
import com.labelhub.common.exception.ErrorCode;
import com.labelhub.common.service.AgentService;
import com.labelhub.common.service.MaterialUploadService;
import com.labelhub.common.util.RedisUtil;
import com.labelhub.modules.dataset.entity.DatasetItem;
import com.labelhub.modules.dataset.mapper.DatasetItemMapper;
import com.labelhub.modules.review.convert.ReviewConvert;
import com.labelhub.modules.review.dto.ReviewModifyDTO;
import com.labelhub.modules.review.dto.ReviewRejectDTO;
import com.labelhub.modules.review.dto.ReviewTaskQueryDTO;
import com.labelhub.modules.review.enums.ReviewDisplayStatusEnum;
import com.labelhub.modules.review.service.ReviewService;
import com.labelhub.modules.review.vo.ReviewItemDetailVO;
import com.labelhub.modules.review.vo.ReviewItemNavigationVO;
import com.labelhub.modules.review.vo.ReviewMaterialVO;
import com.labelhub.modules.review.vo.ReviewProgressVO;
import com.labelhub.modules.review.vo.ReviewTaskVO;
import com.labelhub.modules.review.vo.ReviewWorkspaceInitVO;
import com.labelhub.modules.task.entity.Task;
import com.labelhub.modules.task.entity.TaskItem;
import com.labelhub.modules.task.entity.TaskItemFlowRecord;
import com.labelhub.modules.task.entity.TaskProgress;
import com.labelhub.modules.task.entity.TaskReviewer;
import com.labelhub.modules.task.enums.TaskItemStatusEnum;
import com.labelhub.modules.task.enums.TaskStatusEnum;
import com.labelhub.modules.task.mapper.TaskItemFlowRecordMapper;
import com.labelhub.modules.task.mapper.TaskItemMapper;
import com.labelhub.modules.task.mapper.TaskMapper;
import com.labelhub.modules.task.mapper.TaskProgressMapper;
import com.labelhub.modules.task.mapper.TaskReviewerMapper;
import com.labelhub.modules.template.entity.Material;
import com.labelhub.modules.template.handler.MaterialHandler;
import com.labelhub.modules.template.handler.MaterialHandlerFactory;
import com.labelhub.modules.template.mapper.MaterialMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.labelhub.common.constant.RedisKeyConstant.MATERIAL_CACHE_KEY_PREFIX;
import static com.labelhub.common.constant.RedisKeyConstant.MATERIAL_CACHE_TIMEOUT;

/**
 * 人工审核服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private static final String SHOW_ITEM_TYPE = "showItem";
    private static final String LLM_INTERACTION_TYPE = "llmInteraction";
    private static final String FLOW_ACTION_SUBMIT = "SUBMIT";
    private static final String FLOW_ACTION_AI_PRE_REVIEW = "AI_PRE_REVIEW";
    private static final String FLOW_ACTION_APPROVE = "APPROVE";
    private static final String FLOW_ACTION_REJECT = "REJECT";
    private static final String OPERATOR_AI = "AI";

    private final TaskMapper taskMapper;
    private final TaskItemMapper taskItemMapper;
    private final TaskItemFlowRecordMapper taskItemFlowRecordMapper;
    private final TaskReviewerMapper taskReviewerMapper;
    private final TaskProgressMapper taskProgressMapper;
    private final DatasetItemMapper datasetItemMapper;
    private final MaterialMapper materialMapper;
    private final MaterialHandlerFactory materialHandlerFactory;
    private final ReviewConvert reviewConvert;
    private final ObjectMapper objectMapper;
    private final RedisUtil redisUtil;
    private final AgentService agentService;
    private final MaterialUploadService materialUploadService;

    @Override
    public Page<ReviewTaskVO> listMyTasks(ReviewTaskQueryDTO dto) {
        log.info("查询我的审核任务开始");
        try {
            ReviewTaskQueryDTO query = dto == null ? new ReviewTaskQueryDTO() : dto;
            Long reviewerId = AuthUtil.getCurrentUserId();
            List<Long> taskIds = taskItemMapper.selectCurrentReviewTaskIds(reviewerId);
            Page<ReviewTaskVO> result = new Page<>(safePageNum(query.getPageNum()), safePageSize(query.getPageSize()));
            if (taskIds.isEmpty()) {
                result.setRecords(List.of());
                log.info("查询我的审核任务成功：reviewerId={}，total=0", reviewerId);
                return result;
            }

            LambdaQueryWrapper<Task> wrapper = new LambdaQueryWrapper<Task>()
                    .in(Task::getId, taskIds)
                    .eq(Task::getStatus, TaskStatusEnum.REVIEWING.getCode())
                    .orderByDesc(Task::getUpdateTime);
            if (StringUtils.hasText(query.getKeyword())) {
                wrapper.and(item -> item.like(Task::getTitle, query.getKeyword())
                        .or()
                        .like(Task::getDescription, query.getKeyword()));
            }

            Page<Task> taskPage = taskMapper.selectPage(new Page<>(result.getCurrent(), result.getSize()), wrapper);
            List<Long> pageTaskIds = taskPage.getRecords().stream().map(Task::getId).toList();
            Map<Long, TaskProgress> progressMap = listProgressMap(pageTaskIds);
            List<ReviewTaskVO> records = taskPage.getRecords().stream()
                    .map(task -> toReviewTaskVO(task, progressMap.get(task.getId())))
                    .toList();

            result.setRecords(records);
            result.setTotal(taskPage.getTotal());
            log.info("查询我的审核任务成功：reviewerId={}，total={}", reviewerId, taskPage.getTotal());
            return result;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("查询我的审核任务异常：{}", e.getMessage(), e);
            throw new BusinessException("查询我的审核任务失败：" + e.getMessage());
        }
    }

    @Override
    public ReviewWorkspaceInitVO initWorkspace(Long taskId) {
        log.info("初始化审核工作台开始：taskId={}", taskId);
        try {
            Task task = getRequiredTask(taskId);
            assertTaskReviewing(task);
            ensureCurrentReviewerAssigned(taskId);

            ReviewWorkspaceInitVO vo = new ReviewWorkspaceInitVO();
            vo.setTask(reviewConvert.toTaskBaseVO(task));
            vo.setMaterials(listMaterials(task.getTemplateId()));
            vo.setNavigation(listNavigation(taskId));
            vo.setProgress(buildProgress(taskId, taskProgressMapper.selectOne(new LambdaQueryWrapper<TaskProgress>()
                    .eq(TaskProgress::getTaskId, taskId))));
            log.info("初始化审核工作台成功：taskId={}", taskId);
            return vo;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("初始化审核工作台异常：{}", e.getMessage(), e);
            throw new BusinessException("初始化审核工作台失败：" + e.getMessage());
        }
    }

    @Override
    public ReviewItemDetailVO getItemDetail(Long taskItemId) {
        log.info("查询审核项详情开始：taskItemId={}", taskItemId);
        try {
            TaskItem taskItem = getRequiredReviewableTaskItem(taskItemId);
            Task task = getRequiredTask(taskItem.getTaskId());
            assertTaskReviewing(task);
            ensureCurrentReviewerAssigned(taskItem.getTaskId());
            DatasetItem datasetItem = datasetItemMapper.selectById(taskItem.getDatasetItemId());
            if (datasetItem == null) {
                throw new BusinessException(ErrorCode.DATA_ITEM_NOT_FOUND);
            }

            TaskReviewer currentReviewer = getCurrentReviewer(taskItem.getTaskId());
            ReviewDisplayStatusEnum displayStatus = ReviewDisplayStatusEnum.fromReviewContext(
                    taskItem.getStatus(), taskItem.getCurrentReviewOrder(), currentReviewer.getReviewOrder());
            ReviewItemDetailVO vo = new ReviewItemDetailVO();
            vo.setTaskItemId(taskItem.getId());
            vo.setTaskId(taskItem.getTaskId());
            vo.setTaskTitle(task.getTitle());
            vo.setTaskDescription(task.getDescription());
            vo.setStatus(taskItem.getStatus());
            vo.setDisplayStatus(displayStatus.getCode());
            vo.setDisplayStatusName(displayStatus.getDesc());
            vo.setLabelerId(taskItem.getLabelerId());
            vo.setRawData(parseJsonMap(datasetItem.getContentJson()));
            vo.setDataType(datasetItem.getDataType());
            vo.setAnnotationData(parseJsonMap(taskItem.getAnnotationData()));
            vo.setAiPreReviewResult(buildAiPreReviewResult(task, taskItem));
            vo.setReviewComment(taskItem.getReviewComment());
            log.info("查询审核项详情成功：taskItemId={}", taskItemId);
            return vo;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("查询审核项详情异常：{}", e.getMessage(), e);
            throw new BusinessException("查询审核项详情失败：" + e.getMessage());
        }
    }

    @Override
    public JsonNode getAiAuditResult(Long taskItemId) {
        log.info("查询任务项AI预审结果开始：taskItemId={}", taskItemId);
        try {
            TaskItem taskItem = getRequiredReviewableTaskItem(taskItemId);
            Task task = getRequiredTask(taskItem.getTaskId());
            assertTaskReviewing(task);
            ensureCurrentReviewerAssigned(taskItem.getTaskId());
            JsonNode result = agentService.queryAiAuditResultData(taskItem.getId());
            log.info("查询任务项AI预审结果成功：taskItemId={}", taskItemId);
            return result;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("查询任务项AI预审结果异常：{}", e.getMessage(), e);
            throw new BusinessException("查询任务项AI预审结果失败：" + e.getMessage());
        }
    }

    @Override
    public JsonNode listAiAuditResults(Long taskId) {
        log.info("查询任务AI预审结果列表开始：taskId={}", taskId);
        try {
            Task task = getRequiredTask(taskId);
            assertTaskReviewing(task);
            ensureCurrentReviewerAssigned(taskId);
            JsonNode results = agentService.queryAiAuditResultsData(task.getId());
            log.info("查询任务AI预审结果列表成功：taskId={}，count={}", taskId, results.size());
            return results;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("查询任务AI预审结果列表异常：{}", e.getMessage(), e);
            throw new BusinessException("查询任务AI预审结果列表失败：" + e.getMessage());
        }
    }

    @Override
    @Transactional
    public void approve(Long taskItemId) {
        log.info("审核通过开始：taskItemId={}", taskItemId);
        try {
            TaskItem taskItem = getRequiredReviewableTaskItem(taskItemId);
            assertTaskReviewing(getRequiredTask(taskItem.getTaskId()));
            assertCanApprove(taskItem);
            Long reviewerId = AuthUtil.getCurrentUserId();
            ensureCurrentReviewerTurn(taskItem);
            Integer nextReviewOrder = getNextReviewOrder(taskItem);
            boolean finalApprove = nextReviewOrder == null;

            int rows = taskItemMapper.update(null, new LambdaUpdateWrapper<TaskItem>()
                    .eq(TaskItem::getId, taskItemId)
                    .eq(TaskItem::getStatus, taskItem.getStatus())
                    .eq(TaskItem::getCurrentReviewOrder, taskItem.getCurrentReviewOrder())
                    .set(TaskItem::getStatus, finalApprove
                            ? TaskItemStatusEnum.REVIEWED.getCode()
                            : TaskItemStatusEnum.REVIEWING.getCode())
                    .set(TaskItem::getCurrentReviewOrder, nextReviewOrder)
                    .set(TaskItem::getReviewerId, reviewerId)
                    .set(TaskItem::getReviewComment, null));
            if (rows == 0) {
                throw new BusinessException("审核项状态已变化，请刷新后重试");
            }
            saveFlowRecord(taskItem, reviewerId.toString(), FLOW_ACTION_APPROVE,
                    parseJsonMap(taskItem.getAnnotationData()), null);
            if (finalApprove) {
                updateProgressOnApprove(taskItem);
            }
            tryEndTask(taskItem.getTaskId());
            log.info("审核通过成功：taskItemId={}", taskItemId);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("审核通过异常：{}", e.getMessage(), e);
            throw new BusinessException("审核通过失败：" + e.getMessage());
        }
    }

    @Override
    @Transactional
    public void reject(Long taskItemId, ReviewRejectDTO dto) {
        log.info("审核驳回开始：taskItemId={}", taskItemId);
        try {
            if (dto == null || !StringUtils.hasText(dto.getReviewComment())) {
                throw new BusinessException("审核意见不能为空");
            }
            TaskItem taskItem = getRequiredReviewableTaskItem(taskItemId);
            assertTaskReviewing(getRequiredTask(taskItem.getTaskId()));
            ensureCurrentReviewerTurn(taskItem);
            if (!TaskItemStatusEnum.REVIEWING.getCode().equals(taskItem.getStatus())) {
                throw new BusinessException("当前状态不允许驳回");
            }
            Long reviewerId = AuthUtil.getCurrentUserId();

            int rows = taskItemMapper.update(null, new LambdaUpdateWrapper<TaskItem>()
                    .eq(TaskItem::getId, taskItemId)
                    .eq(TaskItem::getStatus, taskItem.getStatus())
                    .eq(TaskItem::getCurrentReviewOrder, taskItem.getCurrentReviewOrder())
                    .set(TaskItem::getStatus, TaskItemStatusEnum.REJECTED.getCode())
                    .set(TaskItem::getCurrentReviewOrder, null)
                    .set(TaskItem::getReviewerId, reviewerId)
                    .set(TaskItem::getReviewComment, dto.getReviewComment().trim()));
            if (rows == 0) {
                throw new BusinessException("审核项状态已变化，请刷新后重试");
            }
            saveFlowRecord(taskItem, reviewerId.toString(), FLOW_ACTION_REJECT,
                    parseJsonMap(taskItem.getAnnotationData()), dto.getReviewComment().trim());
            taskProgressMapper.updateCounters(taskItem.getTaskId(), 0, 0, -1, 1, 0);
            log.info("审核驳回成功：taskItemId={}", taskItemId);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("审核驳回异常：{}", e.getMessage(), e);
            throw new BusinessException("审核驳回失败：" + e.getMessage());
        }
    }

    @Override
    @Transactional
    public void modify(Long taskItemId, ReviewModifyDTO dto) {
        modify(taskItemId, dto, Map.of());
    }

    @Override
    @Transactional
    public void modify(Long taskItemId, ReviewModifyDTO dto, Map<String, MultipartFile> componentFiles) {
        log.info("审核修改并通过开始：taskItemId={}", taskItemId);
        try {
            if (dto == null || dto.getAnnotationData() == null) {
                throw new BusinessException("标注数据不能为空");
            }
            TaskItem taskItem = getRequiredReviewableTaskItem(taskItemId);
            Task task = getRequiredTask(taskItem.getTaskId());
            assertTaskReviewing(task);
            assertCanApprove(taskItem);
            ensureCurrentReviewerTurn(taskItem);
            List<Material> materials = getCachedMaterials(task.getTemplateId());
            Map<String, Object> annotationData = materialUploadService.fillSubmissionUploadUrls(
                    materials, dto.getAnnotationData(), componentFiles);
            validateRequiredMaterials(materials, annotationData);
            Long reviewerId = AuthUtil.getCurrentUserId();
            Integer nextReviewOrder = getNextReviewOrder(taskItem);
            boolean finalApprove = nextReviewOrder == null;

            int rows = taskItemMapper.update(null, new LambdaUpdateWrapper<TaskItem>()
                    .eq(TaskItem::getId, taskItemId)
                    .eq(TaskItem::getStatus, taskItem.getStatus())
                    .eq(TaskItem::getCurrentReviewOrder, taskItem.getCurrentReviewOrder())
                    .set(TaskItem::getAnnotationData, toJson(annotationData))
                    .set(TaskItem::getStatus, finalApprove
                            ? TaskItemStatusEnum.REVIEWED.getCode()
                            : TaskItemStatusEnum.REVIEWING.getCode())
                    .set(TaskItem::getCurrentReviewOrder, nextReviewOrder)
                    .set(TaskItem::getReviewerId, reviewerId)
                    .set(TaskItem::getReviewComment, null));
            if (rows == 0) {
                throw new BusinessException("审核项状态已变化，请刷新后重试");
            }
            saveFlowRecord(taskItem, reviewerId.toString(), FLOW_ACTION_APPROVE,
                    annotationData, null);
            if (finalApprove) {
                updateProgressOnApprove(taskItem);
            }
            tryEndTask(taskItem.getTaskId());
            log.info("审核修改并通过成功：taskItemId={}", taskItemId);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("审核修改并通过异常：{}", e.getMessage(), e);
            throw new BusinessException("审核修改并通过失败：" + e.getMessage());
        }
    }

    private List<Long> listAssignedTaskIds(Long reviewerId) {
        return taskReviewerMapper.selectList(new LambdaQueryWrapper<TaskReviewer>()
                        .select(TaskReviewer::getTaskId)
                        .eq(TaskReviewer::getReviewerId, reviewerId))
                .stream()
                .map(TaskReviewer::getTaskId)
                .toList();
    }

    private void ensureCurrentReviewerAssigned(Long taskId) {
        Long count = taskReviewerMapper.selectCount(new LambdaQueryWrapper<TaskReviewer>()
                .eq(TaskReviewer::getTaskId, taskId)
                .eq(TaskReviewer::getReviewerId, AuthUtil.getCurrentUserId()));
        if (count == null || count == 0) {
            throw new BusinessException("无权访问该审核任务");
        }
    }

    /**
     * 校验当前任务项是否轮到当前审核员处理。
     */
    private TaskReviewer ensureCurrentReviewerTurn(TaskItem taskItem) {
        TaskReviewer reviewer = getCurrentReviewer(taskItem.getTaskId());
        if (reviewer != null && reviewer.getReviewOrder().equals(taskItem.getCurrentReviewOrder())) {
            return reviewer;
        }
        if (reviewer == null) {
            throw new BusinessException("当前审核项未轮到当前审核员处理");
        }
        throw new BusinessException("当前审核项未轮到当前审核员处理");
    }

    /**
     * 查询当前用户在任务审核链路中的审核员配置。
     */
    private TaskReviewer getCurrentReviewer(Long taskId) {
        TaskReviewer reviewer = taskReviewerMapper.selectOne(new LambdaQueryWrapper<TaskReviewer>()
                .eq(TaskReviewer::getTaskId, taskId)
                .eq(TaskReviewer::getReviewerId, AuthUtil.getCurrentUserId()));
        if (reviewer == null) {
            throw new BusinessException("无权访问该审核任务");
        }
        return reviewer;
    }

    /**
     * 查询当前审核通过后的下一审顺序。
     */
    private Integer getNextReviewOrder(TaskItem taskItem) {
        TaskReviewer next = taskReviewerMapper.selectOne(new LambdaQueryWrapper<TaskReviewer>()
                .select(TaskReviewer::getReviewOrder)
                .eq(TaskReviewer::getTaskId, taskItem.getTaskId())
                .eq(TaskReviewer::getReviewOrder, taskItem.getCurrentReviewOrder() + 1));
        return next == null ? null : next.getReviewOrder();
    }

    private Task getRequiredTask(Long taskId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND);
        }
        return task;
    }

    private void assertTaskReviewing(Task task) {
        if (!TaskStatusEnum.REVIEWING.getCode().equals(task.getStatus())) {
            throw new BusinessException("当前任务未进入审核阶段");
        }
    }

    private TaskItem getRequiredReviewableTaskItem(Long taskItemId) {
        TaskItem taskItem = taskItemMapper.selectById(taskItemId);
        if (taskItem == null) {
            throw new BusinessException("任务数据项不存在");
        }
        if (!isReviewableStatus(taskItem.getStatus())) {
            throw new BusinessException("当前任务数据项不在审核范围内");
        }
        return taskItem;
    }

    private boolean isReviewableStatus(String status) {
        return TaskItemStatusEnum.REVIEWING.getCode().equals(status)
                || TaskItemStatusEnum.REJECTED.getCode().equals(status)
                || TaskItemStatusEnum.REVIEWED.getCode().equals(status);
    }

    private void assertCanApprove(TaskItem taskItem) {
        if (!TaskItemStatusEnum.REVIEWING.getCode().equals(taskItem.getStatus())) {
            throw new BusinessException("当前状态不允许审核通过");
        }
    }

    private List<ReviewItemNavigationVO> listNavigation(Long taskId) {
        TaskReviewer currentReviewer = getCurrentReviewer(taskId);
        List<TaskItem> items = taskItemMapper.selectList(new LambdaQueryWrapper<TaskItem>()
                .select(TaskItem::getId, TaskItem::getStatus, TaskItem::getCurrentReviewOrder)
                .eq(TaskItem::getTaskId, taskId)
                .in(TaskItem::getStatus,
                        TaskItemStatusEnum.REVIEWING.getCode(),
                        TaskItemStatusEnum.REJECTED.getCode(),
                        TaskItemStatusEnum.REVIEWED.getCode())
                .orderByAsc(TaskItem::getId));
        List<ReviewItemNavigationVO> navigation = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            TaskItem item = items.get(i);
            ReviewDisplayStatusEnum displayStatus = ReviewDisplayStatusEnum.fromReviewContext(
                    item.getStatus(), item.getCurrentReviewOrder(), currentReviewer.getReviewOrder());
            ReviewItemNavigationVO vo = new ReviewItemNavigationVO();
            vo.setIndex(i + 1);
            vo.setTaskItemId(item.getId());
            vo.setStatus(item.getStatus());
            vo.setDisplayStatus(displayStatus.getCode());
            vo.setDisplayStatusName(displayStatus.getDesc());
            navigation.add(vo);
        }
        return navigation;
    }

    private Map<Long, TaskProgress> listProgressMap(List<Long> taskIds) {
        if (taskIds.isEmpty()) {
            return Map.of();
        }
        return taskProgressMapper.selectList(new LambdaQueryWrapper<TaskProgress>()
                        .in(TaskProgress::getTaskId, taskIds))
                .stream()
                .collect(Collectors.toMap(TaskProgress::getTaskId, Function.identity()));
    }

    private ReviewTaskVO toReviewTaskVO(Task task, TaskProgress progress) {
        ReviewProgressVO reviewProgress = buildProgress(task.getId(), progress);
        ReviewTaskVO vo = new ReviewTaskVO();
        vo.setTaskId(task.getId());
        vo.setTaskTitle(task.getTitle());
        vo.setTaskDescription(task.getDescription());
        vo.setTaskStatus(task.getStatus());
        vo.setTotalCount(reviewProgress.getTotalCount());
        vo.setPendingCount(reviewProgress.getPendingCount());
        vo.setApprovedCount(reviewProgress.getApprovedCount());
        vo.setRejectedCount(reviewProgress.getRejectedCount());
        vo.setDeadline(task.getDeadline());
        return vo;
    }

    private ReviewProgressVO buildProgress(Long taskId, TaskProgress progress) {
        long total = progress == null || progress.getTotalCount() == null ? 0 : progress.getTotalCount();
        long pending = progress == null || progress.getSubmittedCount() == null ? 0 : progress.getSubmittedCount();
        long approved = progress == null || progress.getReviewedCount() == null ? 0 : progress.getReviewedCount();
        long rejected = progress == null || progress.getRejectedCount() == null ? 0 : progress.getRejectedCount();

        ReviewProgressVO vo = new ReviewProgressVO();
        vo.setTaskId(taskId);
        vo.setTotalCount(total);
        vo.setPendingCount(pending);
        vo.setApprovedCount(approved);
        vo.setRejectedCount(rejected);
        return vo;
    }

    private List<ReviewMaterialVO> listMaterials(Long templateId) {
        return getCachedMaterials(templateId).stream()
                .map(this::toMaterialVO)
                .toList();
    }

    /**
     * 使用与标注工作台相同的 Redis 缓存加载模板物料。
     */
    private List<Material> getCachedMaterials(Long templateId) {
        String cacheKey = MATERIAL_CACHE_KEY_PREFIX + templateId;
        String cached = redisUtil.get(cacheKey);
        if (StringUtils.hasText(cached)) {
            try {
                return objectMapper.readValue(cached, new TypeReference<List<Material>>() {
                });
            } catch (Exception e) {
                log.warn("审核工作台模板物料缓存解析失败，将重新查询数据库：templateId={}，原因={}", templateId, e.getMessage());
                redisUtil.delete(cacheKey);
            }
        }

        List<Material> materials = materialMapper.selectByTemplateId(templateId);
        try {
            redisUtil.set(cacheKey, objectMapper.writeValueAsString(materials),
                    MATERIAL_CACHE_TIMEOUT, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("审核工作台模板物料写入缓存失败：templateId={}，原因={}", templateId, e.getMessage());
        }
        return materials;
    }

    private ReviewMaterialVO toMaterialVO(Material material) {
        ReviewMaterialVO vo = reviewConvert.toMaterialVO(material);
        vo.setConfig(parseJsonMap(material.getConfig()));
        return vo;
    }

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

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception e) {
            throw new BusinessException("JSON 序列化失败：" + e.getMessage());
        }
    }

    /**
     * 查询并返回真实 AI 预审结果。
     */
    private Map<String, Object> buildAiPreReviewResult(Task task, TaskItem taskItem) {
        if (!Boolean.TRUE.equals(task.getAiReviewEnabled())) {
            return null;
        }
        AgentAuditResultDTO result = agentService.queryAiAuditResult(taskItem.getId());
        if (shouldPersistAiPreReviewResult(result)) {
            String comment = buildAiPreReviewComment(result);
            saveOrUpdateAiPreReviewFlowRecord(taskItem, comment);
        }
        return objectToMap(result);
    }

    /**
     * 判断当前 AI 预审结果是否需要写入流转表。
     */
    private boolean shouldPersistAiPreReviewResult(AgentAuditResultDTO result) {
        if (result == null || !StringUtils.hasText(result.getStatus())) {
            return true;
        }
        String status = result.getStatus().trim().toLowerCase();
        return !"pending".equals(status) && !"processing".equals(status);
    }

    /**
     * 构建 AI 预审流转说明。
     */
    private String buildAiPreReviewComment(AgentAuditResultDTO result) {
        if (result == null || !StringUtils.hasText(result.getStatus())
                || !"completed".equalsIgnoreCase(result.getStatus())) {
            return "审核失败";
        }
        if (!StringUtils.hasText(result.getAuditStatus()) || !StringUtils.hasText(result.getSummary())) {
            return "审核失败";
        }
        String prefix = switch (result.getAuditStatus()) {
            case "pass" -> "通过";
            case "manual_review" -> "人工复审";
            case "reject" -> "驳回";
            default -> "审核失败";
        };
        if ("审核失败".equals(prefix)) {
            return prefix;
        }
        return prefix + "：" + result.getSummary();
    }

    /**
     * 每轮提交仅保留一条 AI 预审流转记录，返工重提后会新增新一轮记录。
     */
    private void saveOrUpdateAiPreReviewFlowRecord(TaskItem taskItem, String comment) {
        TaskItemFlowRecord latestSubmit = taskItemFlowRecordMapper.selectOne(new LambdaQueryWrapper<TaskItemFlowRecord>()
                .eq(TaskItemFlowRecord::getTaskItemId, taskItem.getId())
                .eq(TaskItemFlowRecord::getOperatorAction, FLOW_ACTION_SUBMIT)
                .orderByDesc(TaskItemFlowRecord::getOperatorOrder)
                .last("LIMIT 1"));
        TaskItemFlowRecord latestAiReview = taskItemFlowRecordMapper.selectOne(new LambdaQueryWrapper<TaskItemFlowRecord>()
                .eq(TaskItemFlowRecord::getTaskItemId, taskItem.getId())
                .eq(TaskItemFlowRecord::getOperatorAction, FLOW_ACTION_AI_PRE_REVIEW)
                .orderByDesc(TaskItemFlowRecord::getOperatorOrder)
                .last("LIMIT 1"));
        String annotationSnapshot = toJson(parseJsonMap(taskItem.getAnnotationData()));
        if (latestAiReview != null && latestSubmit != null
                && latestAiReview.getOperatorOrder() > latestSubmit.getOperatorOrder()) {
            updateAiPreReviewFlowRecord(latestAiReview, annotationSnapshot, comment);
            return;
        }
        if (latestAiReview != null && latestSubmit == null) {
            updateAiPreReviewFlowRecord(latestAiReview, annotationSnapshot, comment);
            return;
        }
        saveFlowRecord(taskItem, OPERATOR_AI, FLOW_ACTION_AI_PRE_REVIEW,
                parseJsonMap(taskItem.getAnnotationData()), comment);
    }

    /**
     * 更新当前轮次已有的 AI 预审流转记录。
     */
    private void updateAiPreReviewFlowRecord(TaskItemFlowRecord latestAiReview, String annotationSnapshot, String comment) {
        if (Objects.equals(latestAiReview.getAnnotationSnapshot(), annotationSnapshot)
                && Objects.equals(latestAiReview.getComment(), comment)) {
            return;
        }
        TaskItemFlowRecord update = new TaskItemFlowRecord();
        update.setId(latestAiReview.getId());
        update.setAnnotationSnapshot(annotationSnapshot);
        update.setComment(comment);
        taskItemFlowRecordMapper.updateById(update);
    }

    /**
     * 保存任务项流转记录。
     */
    private void saveFlowRecord(TaskItem taskItem, String operatorId, String operatorAction,
                                Map<String, Object> annotationData, String comment) {
        TaskItemFlowRecord record = new TaskItemFlowRecord();
        record.setTaskItemId(taskItem.getId());
        record.setOperatorId(operatorId);
        record.setOperatorAction(operatorAction);
        record.setOperatorOrder(taskItemFlowRecordMapper.selectNextOperatorOrder(taskItem.getId()));
        record.setAnnotationSnapshot(toJson(annotationData));
        record.setComment(comment);
        taskItemFlowRecordMapper.insert(record);
    }

    /**
     * 将对象安全转换为 Map 结构返回前端。
     */
    private Map<String, Object> objectToMap(Object value) {
        if (value == null) {
            return null;
        }
        return objectMapper.convertValue(value, new TypeReference<LinkedHashMap<String, Object>>() {
        });
    }

    private void updateProgressOnApprove(TaskItem taskItem) {
        if (TaskItemStatusEnum.REVIEWING.getCode().equals(taskItem.getStatus())) {
            taskProgressMapper.updateCounters(taskItem.getTaskId(), 0, 0, -1, 0, 1);
        }
    }

    private void tryEndTask(Long taskId) {
        Task task = getRequiredTask(taskId);
        if (!TaskStatusEnum.REVIEWING.getCode().equals(task.getStatus())) {
            return;
        }
        TaskProgress progress = taskProgressMapper.selectOne(new LambdaQueryWrapper<TaskProgress>()
                .eq(TaskProgress::getTaskId, taskId));
        if (progress == null || progress.getTotalCount() == null || progress.getReviewedCount() == null) {
            return;
        }
        if (progress.getTotalCount() > 0 && progress.getReviewedCount() >= progress.getTotalCount()) {
            Task update = new Task();
            update.setId(taskId);
            update.setStatus(TaskStatusEnum.ENDED.getCode());
            taskMapper.updateById(update);
            log.info("审核全部通过，任务自动结束：taskId={}", taskId);
        }
    }

    private long safePageNum(Integer pageNum) {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    private long safePageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? 10 : pageSize;
    }
}
