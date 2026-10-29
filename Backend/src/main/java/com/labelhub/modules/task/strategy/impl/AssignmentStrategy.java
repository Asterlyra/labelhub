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
import com.labelhub.modules.task.enums.DistributionStrategyEnum;
import com.labelhub.modules.task.enums.TaskItemStatusEnum;
import com.labelhub.modules.task.mapper.TaskItemMapper;
import com.labelhub.modules.task.strategy.DistributionStrategy;
import com.labelhub.modules.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 指派分发策略：发布任务时将配额平均分配给指定标注员。
 */
@Component
@RequiredArgsConstructor
public class AssignmentStrategy implements DistributionStrategy {

    private static final int BATCH_SIZE = 500;

    private final DatasetItemMapper datasetItemMapper;
    private final TaskItemMapper taskItemMapper;
    private final ObjectMapper objectMapper;
    private final UserService userService;

    @Override
    public String strategyType() {
        return DistributionStrategyEnum.ASSIGNMENT.getCode();
    }

    @Override
    public void validateConfig(TaskDistributionConfig config) {
        List<Long> labelerIds = parseLabelerIds(config);
        if (labelerIds.isEmpty()) {
            throw new BusinessException("指派策略必须选择至少一名标注员");
        }
        for (Long labelerId : labelerIds) {
            if (!userService.getUserRoles(labelerId).contains(RoleConstant.LABELER)) {
                throw new BusinessException("指派用户不是标注员：" + labelerId);
            }
        }
    }

    @Override
    public void initDistribution(Task task, TaskDistributionConfig config) {
        Long existingCount = taskItemMapper.selectCount(new LambdaQueryWrapper<TaskItem>()
                .eq(TaskItem::getTaskId, task.getId()));
        if (existingCount != null && existingCount > 0) {
            return;
        }

        List<Long> labelerIds = parseLabelerIds(config);
        List<DatasetItem> datasetItems = loadDatasetItems(task);
        List<TaskItem> taskItems = buildAssignedTaskItems(task, datasetItems, labelerIds);
        insertTaskItemsBatch(taskItems);
    }

    @Override
    public List<TaskItem> claimItem(Task task, TaskDistributionConfig config, Long labelerId) {
        throw new BusinessException("当前任务为指派策略，无需领取");
    }

    @Override
    public long getAvailableCount(Long taskId) {
        return 0;
    }

    private List<DatasetItem> loadDatasetItems(Task task) {
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
        return datasetItems;
    }

    private List<TaskItem> buildAssignedTaskItems(Task task, List<DatasetItem> datasetItems, List<Long> labelerIds) {
        int total = datasetItems.size();
        int labelerCount = labelerIds.size();
        int baseCount = total / labelerCount;
        int remainder = total % labelerCount;

        List<TaskItem> taskItems = new ArrayList<>();
        int cursor = 0;
        for (int i = 0; i < labelerCount; i++) {
            int assignCount = baseCount + (i < remainder ? 1 : 0);
            Long labelerId = labelerIds.get(i);
            for (int j = 0; j < assignCount; j++) {
                DatasetItem datasetItem = datasetItems.get(cursor++);
                TaskItem taskItem = new TaskItem();
                taskItem.setTaskId(task.getId());
                taskItem.setDatasetItemId(datasetItem.getId());
                taskItem.setLabelerId(labelerId);
                taskItem.setStatus(TaskItemStatusEnum.CLAIMED.getCode());
                taskItems.add(taskItem);
            }
        }
        return taskItems;
    }

    private List<Long> parseLabelerIds(TaskDistributionConfig config) {
        try {
            if (config == null || !StringUtils.hasText(config.getConfig())) {
                throw new BusinessException("指派策略必须配置标注员");
            }
            Map<String, Object> value = objectMapper.readValue(config.getConfig(), new TypeReference<>() {
            });
            Object rawLabelerIds = value.get("labelerIds");
            List<?> values = objectMapper.convertValue(rawLabelerIds, new TypeReference<List<?>>() {
            });
            if (CollectionUtils.isEmpty(values)) {
                throw new BusinessException("指派策略必须选择至少一名标注员");
            }

            Set<Long> deduplicated = new LinkedHashSet<>();
            for (Object valueItem : values) {
                Long labelerId = parseLong(valueItem);
                if (labelerId == null || labelerId <= 0) {
                    throw new BusinessException("标注员 ID 不合法：" + valueItem);
                }
                if (!deduplicated.add(labelerId)) {
                    throw new BusinessException("指派标注员不能重复：" + labelerId);
                }
            }
            return new ArrayList<>(deduplicated);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("指派策略配置解析失败：" + e.getMessage());
        }
    }

    private Long parseLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            return Long.parseLong(text.trim());
        }
        return null;
    }

    private void insertTaskItemsBatch(List<TaskItem> taskItems) {
        for (int start = 0; start < taskItems.size(); start += BATCH_SIZE) {
            int end = Math.min(start + BATCH_SIZE, taskItems.size());
            taskItemMapper.insertBatch(taskItems.subList(start, end));
        }
    }
}
