package com.labelhub.modules.task.strategy.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelhub.common.constant.RoleConstant;
import com.labelhub.common.exception.BusinessException;
import com.labelhub.modules.dataset.entity.DatasetItem;
import com.labelhub.modules.dataset.mapper.DatasetItemMapper;
import com.labelhub.modules.task.entity.Task;
import com.labelhub.modules.task.entity.TaskDistributionConfig;
import com.labelhub.modules.task.entity.TaskItem;
import com.labelhub.modules.task.entity.TaskProgress;
import com.labelhub.modules.task.enums.DistributionStrategyEnum;
import com.labelhub.modules.task.enums.TaskItemStatusEnum;
import com.labelhub.modules.task.mapper.TaskItemMapper;
import com.labelhub.modules.task.mapper.TaskProgressMapper;
import com.labelhub.modules.task.strategy.DistributionStrategy;
import com.labelhub.modules.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 先到先得分发策略。
 */
@Component
@RequiredArgsConstructor
public class FirstComeFirstServedStrategy implements DistributionStrategy {

    private static final int BATCH_SIZE = 500;

    private final DatasetItemMapper datasetItemMapper;
    private final TaskItemMapper taskItemMapper;
    private final TaskProgressMapper taskProgressMapper;
    private final ObjectMapper objectMapper;
    private final UserService userService;

    @Override
    public String strategyType() {
        return DistributionStrategyEnum.FIRST_COME_FIRST_SERVED.getCode();
    }

    @Override
    public void validateConfig(TaskDistributionConfig config) {
        StrategyConfig strategyConfig = parseConfig(config);
        if (strategyConfig.maxClaimPerUser() <= 0 || strategyConfig.claimCount() <= 0) {
            throw new BusinessException("先到先得策略配置必须大于0");
        }
        if (strategyConfig.claimCount() > strategyConfig.maxClaimPerUser()) {
            throw new BusinessException("单次领取数量不能大于每人最大领取数量");
        }
    }

    @Override
    public void initDistribution(Task task, TaskDistributionConfig config) {
        Long existingCount = taskItemMapper.selectCount(new LambdaQueryWrapper<TaskItem>().eq(TaskItem::getTaskId, task.getId()));
        if (existingCount != null && existingCount > 0) {
            return;
        }

        LambdaQueryWrapper<DatasetItem> wrapper = new LambdaQueryWrapper<DatasetItem>()
                .eq(DatasetItem::getDatasetId, task.getDatasetId())
                .eq(DatasetItem::getParseStatus, "SUCCESS")
                .orderByAsc(DatasetItem::getId);
        if (task.getQuota() != null && task.getQuota() > 0) {
            wrapper.last("LIMIT " + task.getQuota());
        }

        List<DatasetItem> datasetItems = datasetItemMapper.selectList(wrapper);
        if (datasetItems.isEmpty()) {
            throw new BusinessException("数据集中没有可分发的数据项");
        }

        List<TaskItem> taskItems = new ArrayList<>();
        for (DatasetItem datasetItem : datasetItems) {
            TaskItem taskItem = new TaskItem();
            taskItem.setTaskId(task.getId());
            taskItem.setDatasetItemId(datasetItem.getId());
            taskItem.setStatus(TaskItemStatusEnum.UNASSIGNED.getCode());
            taskItems.add(taskItem);
        }
        insertTaskItemsBatch(taskItems);
    }

    @Override
    public List<TaskItem> claimItem(Task task, TaskDistributionConfig config, Long labelerId) {
        if (!userService.getUserRoles(labelerId).contains(RoleConstant.LABELER)) {
            throw new BusinessException("当前用户不是标注员，不能领取任务");
        }

        StrategyConfig strategyConfig = parseConfig(config);
        Long claimedCount = taskItemMapper.selectCount(new LambdaQueryWrapper<TaskItem>()
                .eq(TaskItem::getTaskId, task.getId())
                .eq(TaskItem::getLabelerId, labelerId));
        if (claimedCount != null && claimedCount >= strategyConfig.maxClaimPerUser()) {
            throw new BusinessException("已达到当前任务的个人领取上限");
        }

        int remainingClaimCount = strategyConfig.maxClaimPerUser() - (claimedCount == null ? 0 : claimedCount.intValue());
        int claimLimit = Math.min(strategyConfig.claimCount(), remainingClaimCount);

        // 先来先得领取：SELECT ... FOR UPDATE 按 id 锁定最早可用行，后续请求排队等待。
        List<Long> claimedIds = taskItemMapper.selectUnassignedForUpdate(task.getId(), claimLimit);
        if (claimedIds == null || claimedIds.isEmpty()) {
            throw new BusinessException("当前任务数据项已领完");
        }

        int updated = taskItemMapper.batchClaim(claimedIds, labelerId);
        if (updated != claimedIds.size()) {
            throw new BusinessException("部分任务数据项领取失败，请重试");
        }

        List<TaskItem> taskItems = new ArrayList<>();
        for (Long id : claimedIds) {
            TaskItem item = new TaskItem();
            item.setId(id);
            item.setTaskId(task.getId());
            item.setLabelerId(labelerId);
            item.setStatus(TaskItemStatusEnum.CLAIMED.getCode());
            taskItems.add(item);
        }
        return taskItems;
    }

    @Override
    public long getAvailableCount(Long taskId) {
        TaskProgress progress = taskProgressMapper.selectOne(new LambdaQueryWrapper<TaskProgress>()
                .eq(TaskProgress::getTaskId, taskId));
        return progress == null || progress.getUnassignedCount() == null ? 0 : progress.getUnassignedCount();
    }

    private StrategyConfig parseConfig(TaskDistributionConfig config) {
        try {
            if (config == null || !StringUtils.hasText(config.getConfig())) {
                return new StrategyConfig(10, 1);
            }
            Map<String, Object> value = objectMapper.readValue(config.getConfig(), new TypeReference<>() {
            });
            int maxClaimPerUser = Number.class.isAssignableFrom(value.getOrDefault("maxClaimPerUser", 10).getClass())
                    ? ((Number) value.getOrDefault("maxClaimPerUser", 10)).intValue()
                    : Integer.parseInt(String.valueOf(value.getOrDefault("maxClaimPerUser", 10)));
            int claimCount = Number.class.isAssignableFrom(value.getOrDefault("claimCount", 1).getClass())
                    ? ((Number) value.getOrDefault("claimCount", 1)).intValue()
                    : Integer.parseInt(String.valueOf(value.getOrDefault("claimCount", 1)));
            return new StrategyConfig(maxClaimPerUser, claimCount);
        } catch (Exception e) {
            throw new BusinessException("分发策略配置解析失败：" + e.getMessage());
        }
    }

    private void insertTaskItemsBatch(List<TaskItem> taskItems) {
        for (int start = 0; start < taskItems.size(); start += BATCH_SIZE) {
            int end = Math.min(start + BATCH_SIZE, taskItems.size());
            taskItemMapper.insertBatch(taskItems.subList(start, end));
        }
    }

    private record StrategyConfig(int maxClaimPerUser, int claimCount) {
    }
}
