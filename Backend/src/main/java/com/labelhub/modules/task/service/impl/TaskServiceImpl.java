package com.labelhub.modules.task.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelhub.auth.util.AuthUtil;
import com.labelhub.common.constant.RoleConstant;
import com.labelhub.common.exception.BusinessException;
import com.labelhub.common.exception.ErrorCode;
import com.labelhub.modules.dataset.entity.Dataset;
import com.labelhub.modules.dataset.enums.DatasetStatusEnum;
import com.labelhub.modules.dataset.mapper.DatasetMapper;
import com.labelhub.modules.task.convert.TaskAIReviewConfigConvert;
import com.labelhub.modules.task.convert.TaskConvert;
import com.labelhub.modules.task.dto.DistributionConfigDTO;
import com.labelhub.modules.task.dto.TaskAIReviewConfigDTO;
import com.labelhub.modules.task.dto.TaskAIReviewDimensionDTO;
import com.labelhub.modules.task.dto.TaskCreateDTO;
import com.labelhub.modules.task.dto.TaskItemQueryDTO;
import com.labelhub.modules.task.dto.TaskMarketplaceQueryDTO;
import com.labelhub.modules.task.dto.TaskQueryDTO;
import com.labelhub.modules.task.dto.TaskUpdateDTO;
import com.labelhub.modules.task.entity.Task;
import com.labelhub.modules.task.entity.TaskAIReviewConfig;
import com.labelhub.modules.task.entity.TaskDistributionConfig;
import com.labelhub.modules.task.entity.TaskItem;
import com.labelhub.modules.task.entity.TaskItemFlowRecord;
import com.labelhub.modules.task.entity.TaskProgress;
import com.labelhub.modules.task.entity.TaskReviewer;
import com.labelhub.modules.task.entity.TaskTag;
import com.labelhub.modules.task.enums.DistributionStrategyEnum;
import com.labelhub.modules.task.enums.TaskItemStatusEnum;
import com.labelhub.modules.task.enums.TaskStatusEnum;
import com.labelhub.modules.task.mapper.TaskDistributionConfigMapper;
import com.labelhub.modules.task.mapper.TaskAIReviewConfigMapper;
import com.labelhub.modules.task.mapper.TaskItemFlowRecordMapper;
import com.labelhub.modules.task.mapper.TaskItemMapper;
import com.labelhub.modules.task.mapper.TaskMapper;
import com.labelhub.modules.task.mapper.TaskProgressMapper;
import com.labelhub.modules.task.mapper.TaskReviewerMapper;
import com.labelhub.modules.task.mapper.TaskTagMapper;
import com.labelhub.modules.task.service.TaskService;
import com.labelhub.modules.task.state.TaskStateMachine;
import com.labelhub.modules.task.strategy.DistributionStrategy;
import com.labelhub.modules.task.strategy.DistributionStrategyFactory;
import com.labelhub.modules.task.vo.FlowTimelineVO;
import com.labelhub.modules.task.vo.LabelerTaskVO;
import com.labelhub.modules.task.vo.TaskDetailVO;
import com.labelhub.modules.task.vo.TaskDistributionConfigVO;
import com.labelhub.modules.task.vo.TaskAIReviewConfigVO;
import com.labelhub.modules.task.vo.TaskAIReviewDimensionVO;
import com.labelhub.modules.task.vo.TaskItemVO;
import com.labelhub.modules.task.vo.TaskMarketplaceVO;
import com.labelhub.modules.task.vo.TaskProgressVO;
import com.labelhub.modules.task.vo.TaskRoleDetailVO;
import com.labelhub.modules.task.vo.TaskVO;
import com.labelhub.modules.template.entity.Material;
import com.labelhub.modules.template.entity.Template;
import com.labelhub.modules.template.mapper.MaterialMapper;
import com.labelhub.modules.template.mapper.TemplateMapper;
import com.labelhub.modules.user.entity.User;
import com.labelhub.modules.user.mapper.UserMapper;
import com.labelhub.modules.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 任务管理服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskServiceImpl extends ServiceImpl<TaskMapper, Task> implements TaskService {

    private static final long DEFAULT_PAGE_NUM = 1L;
    private static final long DEFAULT_PAGE_SIZE = 10L;
    private static final BigDecimal DEFAULT_PASS_THRESHOLD = new BigDecimal("85.00");
    private static final BigDecimal DEFAULT_REVIEW_THRESHOLD = new BigDecimal("70.00");
    private static final BigDecimal MIN_SCORE = BigDecimal.ZERO;
    private static final BigDecimal MAX_SCORE = new BigDecimal("100.00");
    private static final String LLM_INTERACTION_TYPE = "llmInteraction";

    private final TaskTagMapper taskTagMapper;
    private final TaskItemMapper taskItemMapper;
    private final TaskItemFlowRecordMapper taskItemFlowRecordMapper;
    private final TaskProgressMapper taskProgressMapper;
    private final TaskReviewerMapper taskReviewerMapper;
    private final TaskDistributionConfigMapper distributionConfigMapper;
    private final TaskAIReviewConfigMapper aiReviewConfigMapper;
    private final DatasetMapper datasetMapper;
    private final TemplateMapper templateMapper;
    private final MaterialMapper materialMapper;
    private final UserMapper userMapper;
    private final TaskConvert taskConvert;
    private final TaskAIReviewConfigConvert aiReviewConfigConvert;
    private final TaskStateMachine taskStateMachine;
    private final DistributionStrategyFactory distributionStrategyFactory;
    private final ObjectMapper objectMapper;
    private final UserService userService;

    @Override
    @Transactional
    public TaskDetailVO createTask(TaskCreateDTO dto) {
        log.info("创建任务开始：title={}", dto.getTitle());
        try {
            validateDatasetAndTemplate(dto.getDatasetId(), dto.getTemplateId(), dto.getQuota());
            validateReviewers(dto.getReviewerIds());

            Task task = taskConvert.toEntity(dto);
            task.setStatus(TaskStatusEnum.DRAFT.getCode());
            task.setCreatorId(AuthUtil.getCurrentUserId());
            task.setAiReviewEnabled(Boolean.TRUE.equals(dto.getAiReviewEnabled()));
            save(task);

            saveTags(task.getId(), dto.getTags());
            saveReviewers(task.getId(), dto.getReviewerIds());
            saveDistributionConfig(task.getId(), dto.getDistributionConfig());
            saveOrDeleteAIReviewConfig(task.getId(), task.getTemplateId(), task.getAiReviewEnabled(),
                    dto.getAiReviewConfig(), false);
            log.info("创建任务成功：taskId={}", task.getId());
            return getTaskDetail(task.getId());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("创建任务异常：{}", e.getMessage(), e);
            throw new BusinessException("创建任务失败：" + e.getMessage());
        }
    }

    @Override
    @Transactional
    public TaskDetailVO updateTask(Long taskId, TaskUpdateDTO dto) {
        log.info("更新任务开始：taskId={}", taskId);
        try {
            Task oldTask = getRequiredTask(taskId);
            if (!TaskStatusEnum.DRAFT.getCode().equals(oldTask.getStatus())) {
                throw new BusinessException("仅草稿状态任务允许编辑");
            }
            validateDatasetAndTemplate(dto.getDatasetId(), dto.getTemplateId(), dto.getQuota());
            if (dto.getReviewerIds() != null) {
                validateReviewers(dto.getReviewerIds());
            }

            Task update = taskConvert.toEntity(dto);
            update.setId(taskId);
            boolean aiReviewEnabled = dto.getAiReviewEnabled() == null
                    ? Boolean.TRUE.equals(oldTask.getAiReviewEnabled())
                    : Boolean.TRUE.equals(dto.getAiReviewEnabled());
            update.setAiReviewEnabled(aiReviewEnabled);
            updateById(update);
            saveTags(taskId, dto.getTags());
            if (dto.getReviewerIds() != null) {
                saveReviewers(taskId, dto.getReviewerIds());
            }
            if (dto.getDistributionConfig() != null) {
                saveDistributionConfig(taskId, dto.getDistributionConfig());
            }
            saveOrDeleteAIReviewConfig(taskId, dto.getTemplateId(), aiReviewEnabled,
                    dto.getAiReviewConfig(), true);
            TaskDetailVO vo = getTaskDetail(taskId);
            log.info("更新任务成功：taskId={}", taskId);
            return vo;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("更新任务异常：{}", e.getMessage(), e);
            throw new BusinessException("更新任务失败：" + e.getMessage());
        }
    }

    @Override
    public Page<TaskVO> listTasks(TaskQueryDTO dto) {
        TaskQueryDTO query = dto == null ? new TaskQueryDTO() : dto;
        long pageNum = positiveOrDefault(query.getPageNum(), DEFAULT_PAGE_NUM);
        long pageSize = positiveOrDefault(query.getPageSize(), DEFAULT_PAGE_SIZE);
        log.info("查询任务列表开始：keyword={}，status={}", query.getKeyword(), query.getStatus());
        try {
            Page<Task> page = new Page<>(pageNum, pageSize);
            LambdaQueryWrapper<Task> wrapper = buildTaskQueryWrapper(query);
            Page<Task> result = page(page, wrapper);
            Page<TaskVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
            List<TaskVO> records = taskConvert.toVOList(result.getRecords());
            fillTaskVOs(records);
            voPage.setRecords(records);
            log.info("查询任务列表成功：total={}", result.getTotal());
            return voPage;
        } catch (Exception e) {
            log.error("查询任务列表失败：{}", e.getMessage(), e);
            throw new BusinessException("查询任务列表失败：" + e.getMessage());
        }
    }

    @Override
    public Page<TaskVO> listMyTasks(TaskQueryDTO dto) {
        TaskQueryDTO query = dto == null ? new TaskQueryDTO() : dto;
        long pageNum = positiveOrDefault(query.getPageNum(), DEFAULT_PAGE_NUM);
        long pageSize = positiveOrDefault(query.getPageSize(), DEFAULT_PAGE_SIZE);
        Long userId = AuthUtil.getCurrentUserId();
        log.info("查询当前管理员任务列表开始：userId={}，keyword={}，status={}",
                userId, query.getKeyword(), query.getStatus());
        try {
            Page<Task> page = new Page<>(pageNum, pageSize);
            LambdaQueryWrapper<Task> wrapper = buildTaskQueryWrapper(query)
                    .eq(Task::getCreatorId, userId);
            Page<Task> result = page(page, wrapper);
            Page<TaskVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
            List<TaskVO> records = taskConvert.toVOList(result.getRecords());
            fillTaskVOs(records);
            voPage.setRecords(records);
            log.info("查询当前管理员任务列表成功：userId={}，total={}", userId, result.getTotal());
            return voPage;
        } catch (Exception e) {
            log.error("查询当前管理员任务列表失败：{}", e.getMessage(), e);
            throw new BusinessException("查询当前管理员任务列表失败：" + e.getMessage());
        }
    }

    @Override
    public TaskDetailVO getTaskDetail(Long taskId) {
        log.info("查询任务详情开始：taskId={}", taskId);
        try {
            Task task = getRequiredTask(taskId);
            TaskDetailVO vo = taskConvert.toDetailVO(task);
            fillTaskVO(vo);
            vo.setReviewerIds(listReviewerIds(taskId));
            vo.setDistributionConfig(buildDistributionConfigVO(getDistributionConfig(taskId)));
            vo.setAiReviewConfig(Boolean.TRUE.equals(task.getAiReviewEnabled())
                    ? buildAIReviewConfigVO(getAIReviewConfigOrNull(taskId))
                    : null);
            log.info("查询任务详情成功：taskId={}", taskId);
            return vo;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("查询任务详情异常：{}", e.getMessage(), e);
            throw new BusinessException("查询任务详情失败：" + e.getMessage());
        }
    }

    @Override
    public TaskRoleDetailVO getTaskDetailForCurrentUser(Long taskId) {
        Long currentUserId = AuthUtil.getCurrentUserId();
        log.info("按当前用户角色查询统一任务详情开始：taskId={}，userId={}", taskId, currentUserId);
        try {
            Task task = getRequiredTask(taskId);
            if (AuthUtil.hasRole(RoleConstant.PROJECT_ADMIN)) {
                TaskRoleDetailVO vo = buildBaseTaskRoleDetailVO(task);
                fillAdminTaskDetail(task, vo);
                log.info("按管理员视角查询统一任务详情成功：taskId={}，userId={}", taskId, currentUserId);
                return vo;
            }
            if (AuthUtil.hasRole(RoleConstant.REVIEWER) && isTaskReviewer(taskId, currentUserId)) {
                TaskRoleDetailVO vo = buildBaseTaskRoleDetailVO(task);
                log.info("按审核员视角查询统一任务详情成功：taskId={}，userId={}", taskId, currentUserId);
                return vo;
            }
            if (AuthUtil.hasRole(RoleConstant.LABELER) && canLabelerViewTaskDetail(task, currentUserId)) {
                TaskRoleDetailVO vo = buildBaseTaskRoleDetailVO(task);
                log.info("按标注员视角查询统一任务详情成功：taskId={}，userId={}", taskId, currentUserId);
                return vo;
            }
            throw new BusinessException("无权查看该任务详情");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("按当前用户角色查询统一任务详情异常：{}", e.getMessage(), e);
            throw new BusinessException("查询任务详情失败：" + e.getMessage());
        }
    }

    @Override
    @Transactional
    public void deleteTask(Long taskId) {
        log.info("删除任务开始：taskId={}", taskId);
        try {
            Task task = getRequiredTask(taskId);
            if (!TaskStatusEnum.DRAFT.getCode().equals(task.getStatus())) {
                throw new BusinessException("仅草稿状态任务允许删除");
            }
            removeById(taskId);
            deleteTaskChildren(taskId);
            log.info("删除任务成功：taskId={}", taskId);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("删除任务异常：{}", e.getMessage(), e);
            throw new BusinessException("删除任务失败：" + e.getMessage());
        }
    }

    @Override
    @Transactional
    public TaskDetailVO publishTask(Long taskId) {
        log.info("发布任务开始：taskId={}", taskId);
        try {
            Task task = getRequiredTask(taskId);
            taskStateMachine.assertCanPublish(task);
            if (listReviewerIds(taskId).isEmpty()) {
                throw new BusinessException("发布任务前必须指派至少一名审核员");
            }
            TaskDistributionConfig config = getDistributionConfig(taskId);
            DistributionStrategy strategy = distributionStrategyFactory.getStrategy(config.getStrategyType());
            strategy.validateConfig(config);
            strategy.initDistribution(task, config);
            rebuildProgressFromTaskItems(taskId);
            updateStatus(taskId, TaskStatusEnum.PUBLISHED.getCode());
            log.info("发布任务成功：taskId={}", taskId);
            return getTaskDetail(taskId);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("发布任务异常：{}", e.getMessage(), e);
            throw new BusinessException("发布任务失败：" + e.getMessage());
        }
    }

    @Override
    @Transactional
    public void pauseTask(Long taskId) {
        changeStatus(taskId, TaskStatusEnum.PAUSED.getCode(), "暂停任务", taskStateMachine::assertCanPause);
    }

    @Override
    @Transactional
    public void resumeTask(Long taskId) {
        changeStatus(taskId, TaskStatusEnum.PUBLISHED.getCode(), "恢复任务", taskStateMachine::assertCanResume);
    }

    @Override
    @Transactional
    public void endTask(Long taskId) {
        changeStatus(taskId, TaskStatusEnum.ENDED.getCode(), "结束任务", taskStateMachine::assertCanEnd);
    }

    @Override
    @Transactional
    public Integer claimTask(Long taskId) {
        Long labelerId = AuthUtil.getCurrentUserId();
        log.info("领取任务数据项开始：taskId={}，labelerId={}", taskId, labelerId);
        try {
            Task task = getRequiredTask(taskId);
            if (!TaskStatusEnum.PUBLISHED.getCode().equals(task.getStatus())) {
                throw new BusinessException("仅发布中的任务允许领取");
            }
            TaskDistributionConfig config = getDistributionConfig(taskId);
            DistributionStrategy strategy = distributionStrategyFactory.getStrategy(config.getStrategyType());
            List<TaskItem> taskItems = strategy.claimItem(task, config, labelerId);
            taskProgressMapper.updateCounters(taskId, -taskItems.size(), taskItems.size(), 0, 0, 0);
            log.info("领取任务数据项成功：taskId={}，count={}", taskId, taskItems.size());
            return taskItems.size();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("领取任务数据项异常：{}", e.getMessage(), e);
            throw new BusinessException("领取任务数据项失败：" + e.getMessage());
        }
    }

    private List<Long> listReviewerIds(Long taskId) {
        log.info("查询任务审核员开始：taskId={}", taskId);
        try {
            List<Long> reviewerIds = taskReviewerMapper.selectList(
                            new LambdaQueryWrapper<TaskReviewer>()
                                    .eq(TaskReviewer::getTaskId, taskId)
                                    .orderByAsc(TaskReviewer::getReviewOrder))
                    .stream()
                    .map(TaskReviewer::getReviewerId)
                    .toList();
            log.info("查询任务审核员成功：taskId={}，count={}", taskId, reviewerIds.size());
            return reviewerIds;
        } catch (Exception e) {
            log.error("查询任务审核员失败：{}", e.getMessage(), e);
            throw new BusinessException("查询任务审核员失败：" + e.getMessage());
        }
    }

    @Override
    public TaskProgressVO getProgress(Long taskId) {
        log.info("查询任务进度开始：taskId={}", taskId);
        try {
            Task task = getRequiredTask(taskId);
            TaskProgressVO vo = buildProgress(task);
            log.info("查询任务进度成功：taskId={}", taskId);
            return vo;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("查询任务进度异常：{}", e.getMessage(), e);
            throw new BusinessException("查询任务进度失败：" + e.getMessage());
        }
    }

    @Override
    public Page<TaskItemVO> listTaskItems(Long taskId, TaskItemQueryDTO dto) {
        TaskItemQueryDTO query = dto == null ? new TaskItemQueryDTO() : dto;
        long pageNum = positiveOrDefault(query.getPageNum(), DEFAULT_PAGE_NUM);
        long pageSize = positiveOrDefault(query.getPageSize(), DEFAULT_PAGE_SIZE);
        String status = normalizeTaskItemStatus(query.getStatus());
        log.info("查询任务数据项开始：taskId={}，status={}", taskId, status);
        try {
            getRequiredTask(taskId);
            Page<TaskItem> page = new Page<>(pageNum, pageSize);
            LambdaQueryWrapper<TaskItem> wrapper = new LambdaQueryWrapper<TaskItem>()
                    .eq(TaskItem::getTaskId, taskId)
                    .eq(StringUtils.hasText(status), TaskItem::getStatus, status)
                    .orderByAsc(TaskItem::getId);
            Page<TaskItem> result = taskItemMapper.selectPage(page, wrapper);
            Page<TaskItemVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
            List<TaskItemVO> records = result.getRecords().stream()
                    .map(this::toTaskItemVO)
                    .toList();
            fillTaskItemUserNames(records);
            voPage.setRecords(records);
            log.info("查询任务数据项成功：taskId={}，total={}", taskId, result.getTotal());
            return voPage;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("查询任务数据项异常：{}", e.getMessage(), e);
            throw new BusinessException("查询任务数据项失败：" + e.getMessage());
        }
    }

    private String normalizeTaskItemStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        String normalized = status.trim().toUpperCase();
        TaskItemStatusEnum.fromCode(normalized);
        return normalized;
    }

    @Override
    public Page<TaskMarketplaceVO> listMarketplaceTasks(TaskMarketplaceQueryDTO dto) {
        TaskMarketplaceQueryDTO query = dto == null ? new TaskMarketplaceQueryDTO() : dto;
        long pageNum = positiveOrDefault(query.getPageNum(), DEFAULT_PAGE_NUM);
        long pageSize = positiveOrDefault(query.getPageSize(), DEFAULT_PAGE_SIZE);
        log.info("查询任务广场列表开始：keyword={}，tags={}", query.getKeyword(), query.getTags());
        try {
            Page<Task> page = new Page<>(pageNum, pageSize);
            LambdaQueryWrapper<Task> wrapper = new LambdaQueryWrapper<Task>()
                    .like(StringUtils.hasText(query.getKeyword()), Task::getTitle, query.getKeyword())
                    .eq(Task::getStatus, TaskStatusEnum.PUBLISHED.getCode())
                    .orderByDesc(Task::getCreateTime);
            List<Long> availableTaskIds = selectAvailableTaskIds();
            wrapper.in(!availableTaskIds.isEmpty(), Task::getId, availableTaskIds);
            wrapper.eq(availableTaskIds.isEmpty(), Task::getId, -1L);
            List<String> tags = normalizeTags(query.getTags());
            if (!tags.isEmpty()) {
                List<Long> taskIds = selectTaskIdsByAllTags(tags);
                wrapper.in(!taskIds.isEmpty(), Task::getId, taskIds);
                wrapper.eq(taskIds.isEmpty(), Task::getId, -1L);
            }

            Page<Task> result = page(page, wrapper);
            Page<TaskMarketplaceVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
            voPage.setRecords(result.getRecords().stream()
                    .map(this::toMarketplaceVO)
                    .toList());
            log.info("查询任务广场列表成功：total={}", result.getTotal());
            return voPage;
        } catch (Exception e) {
            log.error("查询任务广场列表失败：{}", e.getMessage(), e);
            throw new BusinessException("查询任务广场列表失败：" + e.getMessage());
        }
    }

    @Override
    public Page<LabelerTaskVO> listMyMarketplaceTasks(TaskMarketplaceQueryDTO dto) {
        TaskMarketplaceQueryDTO query = dto == null ? new TaskMarketplaceQueryDTO() : dto;
        long pageNum = positiveOrDefault(query.getPageNum(), DEFAULT_PAGE_NUM);
        long pageSize = positiveOrDefault(query.getPageSize(), DEFAULT_PAGE_SIZE);
        Long labelerId = AuthUtil.getCurrentUserId();
        log.info("查询我的任务广场列表开始：labelerId={}，keyword={}，tags={}",
                labelerId, query.getKeyword(), query.getTags());
        try {
            List<Long> claimedTaskIds = selectClaimedTaskIds(labelerId);
            Page<LabelerTaskVO> voPage = new Page<>(pageNum, pageSize, 0);
            if (claimedTaskIds.isEmpty()) {
                voPage.setRecords(List.of());
                return voPage;
            }

            Page<Task> page = new Page<>(pageNum, pageSize);
            LambdaQueryWrapper<Task> wrapper = new LambdaQueryWrapper<Task>()
                    .in(Task::getId, claimedTaskIds)
                    .in(Task::getStatus, TaskStatusEnum.PUBLISHED.getCode(), TaskStatusEnum.REVIEWING.getCode())
                    .like(StringUtils.hasText(query.getKeyword()), Task::getTitle, query.getKeyword())
                    .orderByDesc(Task::getUpdateTime);
            List<String> tags = normalizeTags(query.getTags());
            if (!tags.isEmpty()) {
                List<Long> taggedTaskIds = selectTaskIdsByAllTags(tags);
                wrapper.in(!taggedTaskIds.isEmpty(), Task::getId, taggedTaskIds);
                wrapper.eq(taggedTaskIds.isEmpty(), Task::getId, -1L);
            }

            Page<Task> result = page(page, wrapper);
            Page<LabelerTaskVO> resultPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
            Map<Long, LabelerMarketplaceCount> countMap = buildLabelerMarketplaceCountMap(labelerId, result.getRecords());
            resultPage.setRecords(result.getRecords().stream()
                    .map(task -> toLabelerTaskVO(task, countMap.get(task.getId())))
                    .toList());
            log.info("查询我的任务广场列表成功：labelerId={}，total={}", labelerId, result.getTotal());
            return resultPage;
        } catch (Exception e) {
            log.error("查询我的任务广场列表失败：{}", e.getMessage(), e);
            throw new BusinessException("查询我的任务广场列表失败：" + e.getMessage());
        }
    }

    @Override
    public List<String> listMarketplaceTags() {
        log.info("查询任务广场标签开始");
        try {
            List<Long> availableTaskIds = selectAvailableTaskIds();
            if (availableTaskIds.isEmpty()) {
                return List.of();
            }
            List<Long> publishedTaskIds = list(new LambdaQueryWrapper<Task>()
                            .select(Task::getId)
                            .eq(Task::getStatus, TaskStatusEnum.PUBLISHED.getCode())
                            .in(Task::getId, availableTaskIds))
                    .stream()
                    .map(Task::getId)
                    .toList();
            if (publishedTaskIds.isEmpty()) {
                return List.of();
            }
            List<String> tags = taskTagMapper.selectMaps(new QueryWrapper<TaskTag>()
                            .select("DISTINCT tag_name")
                            .in("task_id", publishedTaskIds)
                            .isNotNull("tag_name")
                            .orderByAsc("tag_name"))
                    .stream()
                    .map(row -> String.valueOf(row.get("tag_name")))
                    .filter(StringUtils::hasText)
                    .toList();
            log.info("查询任务广场标签成功：count={}", tags.size());
            return tags;
        } catch (Exception e) {
            log.error("查询任务广场标签失败：{}", e.getMessage(), e);
            throw new BusinessException("查询任务广场标签失败：" + e.getMessage());
        }
    }

    @Override
    public List<FlowTimelineVO> getFlowTimeline(Long taskItemId) {
        log.info("查询任务项流转时间线开始：taskItemId={}", taskItemId);
        try {
            TaskItem taskItem = taskItemMapper.selectById(taskItemId);
            if (taskItem == null) {
                throw new BusinessException("任务数据项不存在");
            }
            assertFlowTimelineAccess(taskItem);

            List<TaskItemFlowRecord> records = taskItemFlowRecordMapper.selectList(
                    new LambdaQueryWrapper<TaskItemFlowRecord>()
                            .eq(TaskItemFlowRecord::getTaskItemId, taskItemId)
                            .orderByAsc(TaskItemFlowRecord::getOperatorOrder));

            List<FlowTimelineVO> timeline = new ArrayList<>();
            for (TaskItemFlowRecord record : records) {
                FlowTimelineVO vo = new FlowTimelineVO();
                vo.setId(record.getId());
                vo.setTaskItemId(record.getTaskItemId());
                vo.setOperatorAction(record.getOperatorAction());
                vo.setOperatorOrder(record.getOperatorOrder());
                vo.setComment(record.getComment());
                vo.setCreateTime(record.getCreateTime());

                vo.setOperatorName(resolveOperatorName(record.getOperatorId()));

                timeline.add(vo);
            }
            log.info("查询任务项流转时间线成功：taskItemId={}，count={}", taskItemId, timeline.size());
            return timeline;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("查询任务项流转时间线异常：{}", e.getMessage(), e);
            throw new BusinessException("查询任务项流转时间线失败：" + e.getMessage());
        }
    }

    /**
     * 校验当前用户是否有权查看该任务项的流转时间线。
     * 项目管理员、标注员本人或该任务的审核员可访问。
     */
    private void assertFlowTimelineAccess(TaskItem taskItem) {
        if (AuthUtil.hasRole(RoleConstant.PROJECT_ADMIN)) {
            return;
        }
        Long currentUserId = AuthUtil.getCurrentUserId();
        if (currentUserId.equals(taskItem.getLabelerId())) {
            return;
        }
        Long reviewerCount = taskReviewerMapper.selectCount(new LambdaQueryWrapper<TaskReviewer>()
                .eq(TaskReviewer::getTaskId, taskItem.getTaskId())
                .eq(TaskReviewer::getReviewerId, currentUserId));
        if (reviewerCount != null && reviewerCount > 0) {
            return;
        }
        throw new BusinessException("无权查看该任务项的流转时间线");
    }

    private String resolveOperatorName(String operatorId) {
        if ("AI".equals(operatorId)) {
            return "AI预审";
        }
        try {
            Long userId = Long.valueOf(operatorId);
            User user = userMapper.selectById(userId);
            if (user != null) {
                return StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername();
            }
        } catch (NumberFormatException ignored) {
        }
        return operatorId;
    }

    private LambdaQueryWrapper<Task> buildTaskQueryWrapper(TaskQueryDTO query) {
        LambdaQueryWrapper<Task> wrapper = new LambdaQueryWrapper<Task>()
                .like(StringUtils.hasText(query.getKeyword()), Task::getTitle, query.getKeyword())
                .eq(query.getDatasetId() != null, Task::getDatasetId, query.getDatasetId())
                .eq(query.getTemplateId() != null, Task::getTemplateId, query.getTemplateId())
                .eq(StringUtils.hasText(query.getStatus()), Task::getStatus, query.getStatus())
                .orderByDesc(Task::getCreateTime);
        if (StringUtils.hasText(query.getTagName())) {
            List<Long> taskIds = taskTagMapper.selectList(new LambdaQueryWrapper<TaskTag>()
                            .eq(TaskTag::getTagName, query.getTagName()))
                    .stream()
                    .map(TaskTag::getTaskId)
                    .toList();
            wrapper.in(!taskIds.isEmpty(), Task::getId, taskIds);
            wrapper.eq(taskIds.isEmpty(), Task::getId, -1L);
        }
        return wrapper;
    }

    private List<String> normalizeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        return tags.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    private List<Long> selectTaskIdsByAllTags(List<String> tags) {
        if (tags.isEmpty()) {
            return List.of();
        }
        return taskTagMapper.selectMaps(new QueryWrapper<TaskTag>()
                        .select("task_id")
                        .in("tag_name", tags)
                        .groupBy("task_id")
                        .having("COUNT(DISTINCT tag_name) = {0}", tags.size()))
                .stream()
                .map(row -> ((Number) row.get("task_id")).longValue())
                .toList();
    }

    private List<Long> selectAvailableTaskIds() {
        return taskProgressMapper.selectList(new LambdaQueryWrapper<TaskProgress>()
                        .select(TaskProgress::getTaskId)
                        .gt(TaskProgress::getUnassignedCount, 0))
                .stream()
                .map(TaskProgress::getTaskId)
                .toList();
    }

    private List<Long> selectClaimedTaskIds(Long labelerId) {
        return taskItemMapper.selectMaps(new QueryWrapper<TaskItem>()
                        .select("DISTINCT task_id")
                        .eq("labeler_id", labelerId))
                .stream()
                .map(row -> ((Number) row.get("task_id")).longValue())
                .toList();
    }

    private void validateDatasetAndTemplate(Long datasetId, Long templateId, Integer quota) {
        Dataset dataset = datasetMapper.selectById(datasetId);
        if (dataset == null) {
            throw new BusinessException("绑定的数据集不存在");
        }
        if (!DatasetStatusEnum.PARSED.getCode().equals(dataset.getStatus())) {
            throw new BusinessException("绑定的数据集未解析完成");
        }
        validateQuotaNotExceedDataset(quota, dataset);
        Template template = templateMapper.selectById(templateId);
        if (template == null) {
            throw new BusinessException(ErrorCode.TEMPLATE_NOT_FOUND);
        }
        if (template.getStatus() == null || template.getStatus() != 1) {
            throw new BusinessException("绑定的模板未启用");
        }
    }

    private void validateQuotaNotExceedDataset(Integer quota, Dataset dataset) {
        if (quota == null || quota <= 0) {
            return;
        }
        int availableCount = dataset.getSuccessCount() == null ? 0 : dataset.getSuccessCount();
        if (quota > availableCount) {
            throw new BusinessException("任务配额不能大于数据集可分发数据项总数，当前可分发数量：" + availableCount);
        }
    }

    private void validateReviewers(List<Long> reviewerIds) {
        Set<Long> distinctReviewerIds = new LinkedHashSet<>();
        if (reviewerIds == null || reviewerIds.isEmpty()) {
            throw new BusinessException("审核员不能为空");
        }
        for (Long reviewerId : reviewerIds) {
            if (reviewerId == null || !distinctReviewerIds.add(reviewerId)) {
                throw new BusinessException("审核员不能重复或为空");
            }
            if (!userService.getUserRoles(reviewerId).contains(RoleConstant.REVIEWER)) {
                throw new BusinessException("用户不是审核员：" + reviewerId);
            }
        }
    }

    private void saveTags(Long taskId, List<String> tags) {
        taskTagMapper.delete(new LambdaQueryWrapper<TaskTag>().eq(TaskTag::getTaskId, taskId));
        if (tags == null || tags.isEmpty()) {
            return;
        }
        for (String tag : tags) {
            if (!StringUtils.hasText(tag)) {
                continue;
            }
            TaskTag taskTag = new TaskTag();
            taskTag.setTaskId(taskId);
            taskTag.setTagName(tag.trim());
            taskTagMapper.insert(taskTag);
        }
    }

    private void saveReviewers(Long taskId, List<Long> reviewerIds) {
        taskReviewerMapper.delete(new LambdaQueryWrapper<TaskReviewer>().eq(TaskReviewer::getTaskId, taskId));
        for (int i = 0; i < reviewerIds.size(); i++) {
            TaskReviewer reviewer = new TaskReviewer();
            reviewer.setTaskId(taskId);
            reviewer.setReviewerId(reviewerIds.get(i));
            reviewer.setReviewOrder(i + 1);
            taskReviewerMapper.insert(reviewer);
        }
    }

    private void saveDistributionConfig(Long taskId, DistributionConfigDTO dto) {
        DistributionConfigDTO configDTO = dto == null ? new DistributionConfigDTO() : dto;
        TaskDistributionConfig config = new TaskDistributionConfig();
        TaskDistributionConfig old = getDistributionConfigOrNull(taskId);
        if (old != null) {
            config.setId(old.getId());
        }
        config.setTaskId(taskId);
        config.setStrategyType(StringUtils.hasText(configDTO.getStrategyType())
                ? configDTO.getStrategyType()
                : DistributionStrategyEnum.FIRST_COME_FIRST_SERVED.getCode());
        Map<String, Object> strategyConfig = new LinkedHashMap<>();
        strategyConfig.put("maxClaimPerUser", configDTO.getMaxClaimPerUser() == null ? 10 : configDTO.getMaxClaimPerUser());
        strategyConfig.put("claimCount", configDTO.getClaimCount() == null ? 1 : configDTO.getClaimCount());
        strategyConfig.put("labelerIds", configDTO.getLabelerIds() == null ? List.of() : configDTO.getLabelerIds());
        config.setConfig(toJson(strategyConfig));
        DistributionStrategy strategy = distributionStrategyFactory.getStrategy(config.getStrategyType());
        strategy.validateConfig(config);
        if (old == null) {
            distributionConfigMapper.insert(config);
        } else {
            distributionConfigMapper.updateById(config);
        }
    }

    private Task getRequiredTask(Long taskId) {
        Task task = getById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND);
        }
        return task;
    }

    private TaskDistributionConfig getDistributionConfig(Long taskId) {
        TaskDistributionConfig config = getDistributionConfigOrNull(taskId);
        if (config == null) {
            throw new BusinessException("任务分发配置不存在");
        }
        return config;
    }

    private TaskDistributionConfig getDistributionConfigOrNull(Long taskId) {
        return distributionConfigMapper.selectOne(new LambdaQueryWrapper<TaskDistributionConfig>().eq(TaskDistributionConfig::getTaskId, taskId));
    }

    /**
     * 根据任务AI预审开关保存或删除配置。
     */
    private void saveOrDeleteAIReviewConfig(Long taskId, Long templateId, boolean enabled,
                                            TaskAIReviewConfigDTO dto, boolean allowExisting) {
        log.info("处理AI预审配置开始：taskId={}，enabled={}", taskId, enabled);
        try {
            if (!enabled) {
                aiReviewConfigMapper.delete(new LambdaQueryWrapper<TaskAIReviewConfig>()
                        .eq(TaskAIReviewConfig::getTaskId, taskId));
                log.info("AI预审未启用，已清理配置：taskId={}", taskId);
                return;
            }

            if (dto == null) {
                TaskAIReviewConfig existing = getAIReviewConfigOrNull(taskId);
                if (allowExisting && existing != null) {
                    validateExistingAIReviewConfig(templateId, existing);
                    log.info("沿用已有AI预审配置：taskId={}", taskId);
                    return;
                }
                throw new BusinessException("启用AI预审时必须填写AI预审配置");
            }

            fillAIReviewDefaults(dto);
            validateAIReviewConfig(templateId, dto);
            TaskAIReviewConfig oldConfig = getAIReviewConfigOrNull(taskId);
            TaskAIReviewConfig config = aiReviewConfigConvert.toEntity(dto);
            if (oldConfig != null) {
                config.setId(oldConfig.getId());
            }
            config.setTaskId(taskId);
            config.setDimensions(toJson(dto.getDimensions()));

            if (oldConfig == null) {
                aiReviewConfigMapper.insert(config);
            } else {
                aiReviewConfigMapper.updateById(config);
            }
            log.info("保存AI预审配置成功：taskId={}", taskId);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("处理AI预审配置异常：{}", e.getMessage(), e);
            throw new BusinessException("处理AI预审配置失败：" + e.getMessage());
        }
    }

    /**
     * 校验已保存的AI预审配置仍匹配当前模板。
     */
    private void validateExistingAIReviewConfig(Long templateId, TaskAIReviewConfig config) {
        TaskAIReviewConfigDTO dto = new TaskAIReviewConfigDTO();
        dto.setReviewPrompt(config.getReviewPrompt());
        dto.setPassThreshold(config.getPassThreshold());
        dto.setReviewThreshold(config.getReviewThreshold());
        dto.setDimensions(parseAIReviewDimensionDTOs(config.getDimensions()));
        validateAIReviewConfig(templateId, dto);
    }

    /**
     * 填充AI预审配置默认阈值。
     */
    private void fillAIReviewDefaults(TaskAIReviewConfigDTO dto) {
        if (dto.getPassThreshold() == null) {
            dto.setPassThreshold(DEFAULT_PASS_THRESHOLD);
        }
        if (dto.getReviewThreshold() == null) {
            dto.setReviewThreshold(DEFAULT_REVIEW_THRESHOLD);
        }
    }

    /**
     * 校验AI预审配置规则。
     */
    private void validateAIReviewConfig(Long templateId, TaskAIReviewConfigDTO dto) {
        if (!StringUtils.hasText(dto.getReviewPrompt())) {
            throw new BusinessException("AI预审Prompt不能为空");
        }
        validateScoreRange("自动通过阈值", dto.getPassThreshold());
        validateScoreRange("人工复审阈值", dto.getReviewThreshold());
        if (dto.getReviewThreshold().compareTo(dto.getPassThreshold()) >= 0) {
            throw new BusinessException("人工复审阈值必须小于自动通过阈值");
        }

        List<TaskAIReviewDimensionDTO> dimensions = dto.getDimensions();
        if (dimensions == null || dimensions.isEmpty()) {
            throw new BusinessException("AI预审评分维度不能为空");
        }
        Set<String> materialFields = listMaterialFields(templateId);
        Set<String> dimensionFields = new LinkedHashSet<>();
        BigDecimal weightTotal = BigDecimal.ZERO;
        for (TaskAIReviewDimensionDTO dimension : dimensions) {
            validateAIReviewDimension(dimension, dimensionFields);
            weightTotal = weightTotal.add(dimension.getWeight());
        }
        if (weightTotal.compareTo(MAX_SCORE) != 0) {
            throw new BusinessException("AI预审评分维度权重总和必须等于100");
        }
        if (!dimensionFields.equals(materialFields)) {
            throw new BusinessException("AI预审评分维度必须与任务模板物料字段一一对应");
        }
    }

    /**
     * 校验单个AI预审评分维度。
     */
    private void validateAIReviewDimension(TaskAIReviewDimensionDTO dimension, Set<String> dimensionFields) {
        if (dimension == null || !StringUtils.hasText(dimension.getField())) {
            throw new BusinessException("AI预审评分维度字段不能为空");
        }
        String field = dimension.getField().trim();
        if (!dimensionFields.add(field)) {
            throw new BusinessException("AI预审评分维度字段重复：" + field);
        }
        dimension.setField(field);
        validateScoreRange("AI预审维度权重", dimension.getWeight());
        validateScoreRange("AI预审维度通过分", dimension.getPassScore());
    }

    /**
     * 校验分值范围。
     */
    private void validateScoreRange(String name, BigDecimal value) {
        if (value == null) {
            throw new BusinessException(name + "不能为空");
        }
        if (value.compareTo(MIN_SCORE) < 0 || value.compareTo(MAX_SCORE) > 0) {
            throw new BusinessException(name + "必须在0到100之间");
        }
    }

    /**
     * 查询模板物料字段集合。
     */
    private Set<String> listMaterialFields(Long templateId) {
        List<Material> materials = materialMapper.selectByTemplateId(templateId);
        Set<String> fields = new LinkedHashSet<>();
        for (Material material : materials) {
            if (LLM_INTERACTION_TYPE.equals(material.getType())) {
                continue;
            }
            if (StringUtils.hasText(material.getField())) {
                fields.add(material.getField().trim());
            }
        }
        if (fields.isEmpty()) {
            throw new BusinessException("任务模板物料字段不能为空");
        }
        return fields;
    }

    /**
     * 查询任务AI预审配置。
     */
    private TaskAIReviewConfig getAIReviewConfigOrNull(Long taskId) {
        return aiReviewConfigMapper.selectByTaskId(taskId);
    }

    /**
     * 构建AI预审配置响应对象。
     */
    private TaskAIReviewConfigVO buildAIReviewConfigVO(TaskAIReviewConfig config) {
        if (config == null) {
            return null;
        }
        TaskAIReviewConfigVO vo = aiReviewConfigConvert.toVO(config);
        vo.setDimensions(parseAIReviewDimensionVOs(config.getDimensions()));
        return vo;
    }

    /**
     * 解析AI预审评分维度请求列表。
     */
    private List<TaskAIReviewDimensionDTO> parseAIReviewDimensionDTOs(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<TaskAIReviewDimensionDTO>>() {
            });
        } catch (Exception e) {
            throw new BusinessException("AI预审评分维度解析失败：" + e.getMessage());
        }
    }

    /**
     * 解析AI预审评分维度响应列表。
     */
    private List<TaskAIReviewDimensionVO> parseAIReviewDimensionVOs(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<TaskAIReviewDimensionVO>>() {
            });
        } catch (Exception e) {
            throw new BusinessException("AI预审评分维度解析失败：" + e.getMessage());
        }
    }

    private void fillTaskVO(TaskVO vo) {
        vo.setTags(listTags(vo.getId()));
        vo.setProgress(buildProgress(vo.getId(), vo.getStatus(), vo.getQuota()));
    }

    /**
     * 构建统一任务详情公共字段。
     */
    private TaskRoleDetailVO buildBaseTaskRoleDetailVO(Task task) {
        TaskRoleDetailVO vo = new TaskRoleDetailVO();
        vo.setId(task.getId());
        vo.setTitle(task.getTitle());
        vo.setDescription(task.getDescription());
        vo.setQuota(task.getQuota());
        vo.setDeadline(task.getDeadline());
        vo.setTags(listTags(task.getId()));
        vo.setProgress(buildProgress(task));
        return vo;
    }

    /**
     * 补充管理员任务详情专属字段。
     */
    private void fillAdminTaskDetail(Task task, TaskRoleDetailVO vo) {
        Dataset dataset = datasetMapper.selectById(task.getDatasetId());
        if (dataset != null) {
            vo.setDatasetName(dataset.getName());
        }
        Template template = templateMapper.selectById(task.getTemplateId());
        if (template != null) {
            vo.setTemplateName(template.getName());
        }
        vo.setStatus(task.getStatus());
        vo.setAiReviewEnabled(Boolean.TRUE.equals(task.getAiReviewEnabled()));
        vo.setReviewerIds(listReviewerIds(task.getId()));
        vo.setDistributionConfig(buildDistributionConfigVO(getDistributionConfig(task.getId())));
        vo.setAiReviewConfig(Boolean.TRUE.equals(task.getAiReviewEnabled())
                ? buildAIReviewConfigVO(getAIReviewConfigOrNull(task.getId()))
                : null);
    }

    private void fillTaskVOs(List<TaskVO> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<Long> taskIds = records.stream().map(TaskVO::getId).toList();
        Map<Long, List<String>> tagMap = listTagMap(taskIds);
        Map<Long, TaskProgress> progressMap = listProgressMap(taskIds);
        for (TaskVO vo : records) {
            vo.setTags(tagMap.getOrDefault(vo.getId(), List.of()));
            vo.setProgress(buildProgress(vo.getId(), vo.getQuota(), progressMap.get(vo.getId())));
        }
    }

    private List<String> listTags(Long taskId) {
        return taskTagMapper.selectList(new LambdaQueryWrapper<TaskTag>().eq(TaskTag::getTaskId, taskId))
                .stream()
                .map(TaskTag::getTagName)
                .toList();
    }

    private Map<Long, List<String>> listTagMap(List<Long> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<String>> tagMap = new LinkedHashMap<>();
        List<TaskTag> tags = taskTagMapper.selectList(new LambdaQueryWrapper<TaskTag>()
                .in(TaskTag::getTaskId, taskIds));
        for (TaskTag tag : tags) {
            tagMap.computeIfAbsent(tag.getTaskId(), key -> new ArrayList<>()).add(tag.getTagName());
        }
        return tagMap;
    }

    private TaskProgressVO buildProgress(Task task) {
        return buildProgress(task.getId(), task.getStatus(), task.getQuota());
    }

    private TaskProgressVO buildProgress(Long taskId, String taskStatus, Integer quota) {
        TaskProgress progress = getTaskProgress(taskId);
        return buildProgress(taskId, quota, progress);
    }

    private TaskProgressVO buildProgress(Long taskId, Integer quota, TaskProgress progress) {
        if (progress != null) {
            return toProgressVO(progress);
        }
        return buildEmptyProgress(taskId, quota);
    }

    private TaskProgressVO buildEmptyProgress(Long taskId, Integer quota) {
        TaskProgressVO vo = new TaskProgressVO();
        vo.setTaskId(taskId);
        vo.setTotalCount(quota == null ? 0L : quota.longValue());
        vo.setUnassignedCount(0L);
        vo.setClaimedCount(0L);
        vo.setSubmittedCount(0L);
        vo.setRejectedCount(0L);
        vo.setReviewedCount(0L);
        return vo;
    }

    private TaskProgress getTaskProgress(Long taskId) {
        return taskProgressMapper.selectOne(new LambdaQueryWrapper<TaskProgress>()
                .eq(TaskProgress::getTaskId, taskId));
    }

    private Map<Long, TaskProgress> listProgressMap(List<Long> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, TaskProgress> progressMap = new LinkedHashMap<>();
        List<TaskProgress> progressList = taskProgressMapper.selectList(new LambdaQueryWrapper<TaskProgress>()
                .in(TaskProgress::getTaskId, taskIds));
        for (TaskProgress progress : progressList) {
            progressMap.put(progress.getTaskId(), progress);
        }
        return progressMap;
    }

    private TaskProgressVO toProgressVO(TaskProgress progress) {
        TaskProgressVO vo = new TaskProgressVO();
        vo.setTaskId(progress.getTaskId());
        vo.setTotalCount(nullToZero(progress.getTotalCount()));
        vo.setUnassignedCount(nullToZero(progress.getUnassignedCount()));
        vo.setClaimedCount(nullToZero(progress.getClaimedCount()));
        vo.setSubmittedCount(nullToZero(progress.getSubmittedCount()));
        vo.setRejectedCount(nullToZero(progress.getRejectedCount()));
        vo.setReviewedCount(nullToZero(progress.getReviewedCount()));
        return vo;
    }

    private long nullToZero(Long value) {
        return value == null ? 0L : value;
    }

    private TaskProgress rebuildProgressFromTaskItems(Long taskId) {
        TaskProgress progress = new TaskProgress();
        progress.setTaskId(taskId);
        progress.setTotalCount(0L);
        progress.setUnassignedCount(0L);
        progress.setClaimedCount(0L);
        progress.setSubmittedCount(0L);
        progress.setRejectedCount(0L);
        progress.setReviewedCount(0L);

        List<Map<String, Object>> rows = taskItemMapper.selectMaps(new QueryWrapper<TaskItem>()
                .select("status", "COUNT(*) AS count")
                .eq("task_id", taskId)
                .groupBy("status"));
        for (Map<String, Object> row : rows) {
            String status = String.valueOf(row.get("status"));
            long count = ((Number) row.get("count")).longValue();
            progress.setTotalCount(progress.getTotalCount() + count);
            fillStatusCount(progress, status, count);
        }
        taskProgressMapper.upsert(progress);
        return progress;
    }

    private void fillStatusCount(TaskProgress progress, String status, long count) {
        if (TaskItemStatusEnum.UNASSIGNED.getCode().equals(status)) {
            progress.setUnassignedCount(count);
        } else if (TaskItemStatusEnum.CLAIMED.getCode().equals(status)
                || TaskItemStatusEnum.DRAFT.getCode().equals(status)) {
            progress.setClaimedCount(progress.getClaimedCount() + count);
        } else if (TaskItemStatusEnum.SUBMITTED.getCode().equals(status)
                || TaskItemStatusEnum.REVIEWING.getCode().equals(status)) {
            progress.setSubmittedCount(progress.getSubmittedCount() + count);
        } else if (TaskItemStatusEnum.REJECTED.getCode().equals(status)) {
            progress.setRejectedCount(count);
        } else if (TaskItemStatusEnum.REVIEWED.getCode().equals(status)) {
            progress.setReviewedCount(count);
        }
    }

    private TaskMarketplaceVO toMarketplaceVO(Task task) {
        TaskMarketplaceVO vo = buildMarketplaceBaseVO(task);

        TaskProgressVO progress = buildProgress(task);
        long unassignedCount = progress.getUnassignedCount() == null ? 0L : progress.getUnassignedCount();
        long totalCount = progress.getTotalCount() == null ? 0L : progress.getTotalCount();
        vo.setAvailableCount(unassignedCount);
        vo.setClaimedCount(Math.max(totalCount - unassignedCount, 0L));
        return vo;
    }

    private TaskMarketplaceVO buildMarketplaceBaseVO(Task task) {
        TaskMarketplaceVO vo = new TaskMarketplaceVO();
        vo.setId(task.getId());
        vo.setTitle(task.getTitle());
        vo.setDescription(task.getDescription());
        vo.setTaskStatus(task.getStatus());
        vo.setTags(listTags(task.getId()));
        vo.setQuota(task.getQuota());
        vo.setRewardRule(task.getRewardRule());
        vo.setDeadline(task.getDeadline());
        vo.setDatasetId(task.getDatasetId());
        vo.setTemplateId(task.getTemplateId());
        vo.setCreatorId(task.getCreatorId());
        vo.setCreateTime(task.getCreateTime());

        Template template = templateMapper.selectById(task.getTemplateId());
        if (template != null) {
            vo.setTemplateName(template.getName());
        }
        User creator = userMapper.selectById(task.getCreatorId());
        if (creator != null) {
            vo.setCreatorName(StringUtils.hasText(creator.getNickname()) ? creator.getNickname() : creator.getUsername());
        }
        return vo;
    }

    /**
     * 判断当前用户是否是任务审核员。
     */
    private boolean isTaskReviewer(Long taskId, Long userId) {
        Long count = taskReviewerMapper.selectCount(new LambdaQueryWrapper<TaskReviewer>()
                .eq(TaskReviewer::getTaskId, taskId)
                .eq(TaskReviewer::getReviewerId, userId));
        return count != null && count > 0;
    }

    /**
     * 判断标注员是否有权查看任务详情。
     */
    private boolean canLabelerViewTaskDetail(Task task, Long labelerId) {
        if (TaskStatusEnum.PUBLISHED.getCode().equals(task.getStatus())) {
            return true;
        }
        Long count = taskItemMapper.selectCount(new LambdaQueryWrapper<TaskItem>()
                .eq(TaskItem::getTaskId, task.getId())
                .eq(TaskItem::getLabelerId, labelerId));
        return count != null && count > 0;
    }

    /**
     * 构建标注员任务列表轻量响应，只返回标注员工作台实际需要的字段。
     */
    private LabelerTaskVO toLabelerTaskVO(Task task, LabelerMarketplaceCount count) {
        LabelerTaskVO vo = new LabelerTaskVO();
        vo.setId(task.getId());
        vo.setTitle(task.getTitle());
        vo.setDescription(task.getDescription());
        vo.setTaskStatus(task.getStatus());
        vo.setRewardRule(task.getRewardRule());
        vo.setDeadline(task.getDeadline());
        LabelerMarketplaceCount safeCount = count == null ? new LabelerMarketplaceCount(0L, 0L, 0L) : count;
        vo.setClaimedCount(safeCount.claimedCount());
        vo.setAnnotatedCount(safeCount.annotatedCount());
        vo.setRejectedCount(safeCount.rejectedCount());
        return vo;
    }

    private Map<Long, LabelerMarketplaceCount> buildLabelerMarketplaceCountMap(Long labelerId, List<Task> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return Map.of();
        }
        List<Long> taskIds = tasks.stream().map(Task::getId).toList();
        List<Map<String, Object>> rows = taskItemMapper.selectLabelerMarketplaceCounts(labelerId, taskIds);
        Map<Long, LabelerMarketplaceCount> countMap = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Long taskId = ((Number) row.get("task_id")).longValue();
            Long claimedCount = ((Number) row.get("claimed_count")).longValue();
            Long annotatedCount = ((Number) row.get("annotated_count")).longValue();
            Long rejectedCount = ((Number) row.get("rejected_count")).longValue();
            countMap.put(taskId, new LabelerMarketplaceCount(claimedCount, annotatedCount, rejectedCount));
        }
        return countMap;
    }

    private record LabelerMarketplaceCount(Long claimedCount, Long annotatedCount, Long rejectedCount) {
    }

    private TaskDistributionConfigVO buildDistributionConfigVO(TaskDistributionConfig config) {
        TaskDistributionConfigVO vo = new TaskDistributionConfigVO();
        vo.setStrategyType(config.getStrategyType());
        vo.setConfig(parseJsonMap(config.getConfig()));
        return vo;
    }

    private TaskItemVO toTaskItemVO(TaskItem taskItem) {
        TaskItemVO vo = taskConvert.toItemVO(taskItem);
        fillTaskItemVO(vo, taskItem == null ? null : taskItem.getAnnotationData());
        return vo;
    }

    private void fillTaskItemUserNames(List<TaskItemVO> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        Set<Long> userIds = new LinkedHashSet<>();
        for (TaskItemVO record : records) {
            if (record.getLabelerId() != null) {
                userIds.add(record.getLabelerId());
            }
            if (record.getReviewerId() != null) {
                userIds.add(record.getReviewerId());
            }
        }
        if (userIds.isEmpty()) {
            return;
        }

        Map<Long, User> userMap = new LinkedHashMap<>();
        for (User user : userMapper.selectBatchIds(userIds)) {
            userMap.put(user.getId(), user);
        }
        for (TaskItemVO record : records) {
            record.setLabelerName(resolveUserDisplayName(userMap.get(record.getLabelerId())));
            record.setReviewerName(resolveUserDisplayName(userMap.get(record.getReviewerId())));
        }
    }

    private String resolveUserDisplayName(User user) {
        if (user == null) {
            return null;
        }
        return StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername();
    }

    private void fillTaskItemVO(TaskItemVO vo) {
        TaskItem taskItem = taskItemMapper.selectById(vo.getId());
        fillTaskItemVO(vo, taskItem == null ? null : taskItem.getAnnotationData());
    }

    private void fillTaskItemVO(TaskItemVO vo, String annotationData) {
        vo.setAnnotationData(parseJsonMap(annotationData));
    }

    private void updateStatus(Long taskId, String status) {
        Task update = new Task();
        update.setId(taskId);
        update.setStatus(status);
        updateById(update);
    }

    private void changeStatus(Long taskId, String targetStatus, String action, java.util.function.Consumer<Task> validator) {
        log.info("{}开始：taskId={}", action, taskId);
        try {
            Task task = getRequiredTask(taskId);
            validator.accept(task);
            updateStatus(taskId, targetStatus);
            log.info("{}成功：taskId={}，status={}", action, taskId, targetStatus);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("{}异常：{}", action, e.getMessage(), e);
            throw new BusinessException(action + "失败：" + e.getMessage());
        }
    }

    private void deleteTaskChildren(Long taskId) {
        taskTagMapper.delete(new LambdaQueryWrapper<TaskTag>().eq(TaskTag::getTaskId, taskId));
        taskItemMapper.delete(new LambdaQueryWrapper<TaskItem>().eq(TaskItem::getTaskId, taskId));
        taskProgressMapper.delete(new LambdaQueryWrapper<TaskProgress>().eq(TaskProgress::getTaskId, taskId));
        taskReviewerMapper.delete(new LambdaQueryWrapper<TaskReviewer>().eq(TaskReviewer::getTaskId, taskId));
        distributionConfigMapper.delete(new LambdaQueryWrapper<TaskDistributionConfig>().eq(TaskDistributionConfig::getTaskId, taskId));
        aiReviewConfigMapper.delete(new LambdaQueryWrapper<TaskAIReviewConfig>().eq(TaskAIReviewConfig::getTaskId, taskId));
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
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BusinessException("JSON 序列化失败：" + e.getMessage());
        }
    }

    private long positiveOrDefault(Integer value, long defaultValue) {
        return value == null || value <= 0 ? defaultValue : value;
    }
}
