package com.labelhub.modules.export.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelhub.common.constant.MinioBucketConstant;
import com.labelhub.common.constant.MinioObjectDirectoryConstant;
import com.labelhub.common.exception.BusinessException;
import com.labelhub.common.util.MinIOUtil;
import com.labelhub.modules.dataset.entity.DatasetItem;
import com.labelhub.modules.dataset.mapper.DatasetItemMapper;
import com.labelhub.modules.export.dto.ExportFieldMappingDTO;
import com.labelhub.modules.export.entity.ExportJob;
import com.labelhub.modules.export.enums.ExportFormatEnum;
import com.labelhub.modules.export.enums.ExportStatusEnum;
import com.labelhub.modules.export.mapper.ExportJobMapper;
import com.labelhub.modules.export.service.ExportAsyncService;
import com.labelhub.modules.export.writer.ExportWriter;
import com.labelhub.modules.task.entity.Task;
import com.labelhub.modules.task.entity.TaskItem;
import com.labelhub.modules.task.enums.TaskItemStatusEnum;
import com.labelhub.modules.task.mapper.TaskItemMapper;
import com.labelhub.modules.task.mapper.TaskMapper;
import com.labelhub.modules.template.entity.Material;
import com.labelhub.modules.template.mapper.MaterialMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExportAsyncServiceImpl implements ExportAsyncService {

    private static final String LLM_INTERACTION_TYPE = "llmInteraction";

    private final ExportJobMapper exportJobMapper;
    private final TaskMapper taskMapper;
    private final TaskItemMapper taskItemMapper;
    private final DatasetItemMapper datasetItemMapper;
    private final MaterialMapper materialMapper;
    private final MinIOUtil minIOUtil;
    private final ObjectMapper objectMapper;
    private final List<ExportWriter> writerList;

    private Map<ExportFormatEnum, ExportWriter> writers;

    @PostConstruct
    public void init() {
        writers = writerList.stream().collect(Collectors.toMap(
                ExportWriter::format,
                Function.identity(),
                (left, right) -> left,
                () -> new EnumMap<>(ExportFormatEnum.class)
        ));
    }

    @Async("exportExecutor")
    @Override
    public void execute(Long exportJobId) {
        ExportJob job = exportJobMapper.selectById(exportJobId);
        if (job == null) {
            log.warn("导出任务不存在，跳过执行：exportJobId={}", exportJobId);
            return;
        }

        markProcessing(exportJobId);
        try {
            ExportFormatEnum format = ExportFormatEnum.fromCode(job.getFormat());
            ExportWriter writer = writers.get(format);
            if (writer == null) {
                throw new BusinessException("未注册导出格式处理器：" + format.getCode());
            }

            List<ExportFieldMappingDTO> fields = readFields(job.getConfigJson());
            List<Map<String, Object>> rows = buildRows(job.getTaskId(), fields);
            byte[] bytes = writer.write(rows, fields);
            String filename = "task-" + job.getTaskId() + "-export-" + job.getId() + format.getExtension();
            MinIOUtil.UploadResult uploadResult = minIOUtil.uploadBytes(bytes, filename, format.getContentType(),
                    MinioBucketConstant.EXPORT, MinioObjectDirectoryConstant.EXPORTS);
            markSuccess(exportJobId, uploadResult.url());
            log.info("导出任务执行成功：exportJobId={}，rows={}", exportJobId, rows.size());
        } catch (Exception e) {
            log.error("导出任务执行失败：exportJobId={}，error={}", exportJobId, e.getMessage(), e);
            markFailed(exportJobId, e.getMessage());
        }
    }

    private List<Map<String, Object>> buildRows(Long taskId, List<ExportFieldMappingDTO> fields) {
        List<TaskItem> taskItems = taskItemMapper.selectList(new LambdaQueryWrapper<TaskItem>()
                .eq(TaskItem::getTaskId, taskId)
                .eq(TaskItem::getStatus, TaskItemStatusEnum.REVIEWED.getCode())
                .orderByAsc(TaskItem::getId));
        if (taskItems.isEmpty()) {
            throw new BusinessException("当前任务没有可导出的审核通过数据");
        }

        Map<Long, DatasetItem> datasetItemMap = loadDatasetItems(taskItems);
        List<Map<String, Object>> mergedRows = new ArrayList<>();
        Set<String> skippedFields = loadSkippedExportFields(taskId);
        Set<String> existingFields = new HashSet<>();
        existingFields.addAll(loadTemplateFields(taskId));
        for (TaskItem taskItem : taskItems) {
            DatasetItem datasetItem = datasetItemMap.get(taskItem.getDatasetItemId());
            if (datasetItem == null) {
                throw new BusinessException("任务项关联的数据集数据不存在：" + taskItem.getDatasetItemId());
            }
            Map<String, Object> merged = new LinkedHashMap<>();
            merged.putAll(parseJsonObject(datasetItem.getContentJson(), "数据集内容"));
            merged.putAll(parseJsonObject(taskItem.getAnnotationData(), "标注结果"));
            skippedFields.forEach(merged::remove);
            existingFields.addAll(merged.keySet());
            existingFields.removeAll(skippedFields);
            mergedRows.add(merged);
        }

        validateSourceFields(fields, existingFields);
        List<Map<String, Object>> exportRows = new ArrayList<>();
        for (Map<String, Object> merged : mergedRows) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (ExportFieldMappingDTO field : fields) {
                row.put(field.getTargetName(), merged.get(field.getSourceName()));
            }
            exportRows.add(row);
        }
        return exportRows;
    }

    private Set<String> loadTemplateFields(Long taskId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("任务不存在");
        }
        return materialMapper.selectByTemplateId(task.getTemplateId()).stream()
                .filter(material -> !shouldSkipExportMaterial(material))
                .map(Material::getField)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
    }

    private Set<String> loadSkippedExportFields(Long taskId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("任务不存在");
        }
        return materialMapper.selectByTemplateId(task.getTemplateId()).stream()
                .filter(this::shouldSkipExportMaterial)
                .map(Material::getField)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
    }

    private boolean shouldSkipExportMaterial(Material material) {
        return LLM_INTERACTION_TYPE.equals(material.getType());
    }

    private Map<Long, DatasetItem> loadDatasetItems(List<TaskItem> taskItems) {
        Collection<Long> datasetItemIds = taskItems.stream()
                .map(TaskItem::getDatasetItemId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        if (datasetItemIds.isEmpty()) {
            throw new BusinessException("任务项未关联数据集数据");
        }
        return datasetItemMapper.selectBatchIds(datasetItemIds).stream()
                .collect(Collectors.toMap(DatasetItem::getId, Function.identity(), (left, right) -> left));
    }

    private void validateSourceFields(List<ExportFieldMappingDTO> fields, Set<String> existingFields) {
        for (ExportFieldMappingDTO field : fields) {
            if (!existingFields.contains(field.getSourceName())) {
                throw new BusinessException("导出字段不存在：" + field.getSourceName());
            }
        }
    }

    private List<ExportFieldMappingDTO> readFields(String configJson) {
        try {
            JsonNode root = objectMapper.readTree(configJson);
            JsonNode fieldsNode = root == null ? null : root.get("fields");
            if (fieldsNode == null || !fieldsNode.isArray() || fieldsNode.isEmpty()) {
                throw new BusinessException("导出字段配置为空");
            }
            return objectMapper.convertValue(fieldsNode, new TypeReference<List<ExportFieldMappingDTO>>() {
            });
        } catch (JsonProcessingException e) {
            throw new BusinessException("导出配置解析失败：" + e.getMessage());
        }
    }

    private Map<String, Object> parseJsonObject(String json, String name) {
        if (!StringUtils.hasText(json)) {
            return new LinkedHashMap<>();
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root == null || root.isNull()) {
                return new LinkedHashMap<>();
            }
            if (!root.isObject()) {
                throw new BusinessException(name + "必须是 JSON 对象");
            }
            return objectMapper.convertValue(root, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (JsonProcessingException e) {
            throw new BusinessException(name + "解析失败：" + e.getMessage());
        }
    }

    private void markProcessing(Long exportJobId) {
        ExportJob update = new ExportJob();
        update.setId(exportJobId);
        update.setStatus(ExportStatusEnum.PROCESSING.getCode());
        update.setErrorMessage(null);
        exportJobMapper.updateById(update);
    }

    private void markSuccess(Long exportJobId, String fileUrl) {
        ExportJob update = new ExportJob();
        update.setId(exportJobId);
        update.setStatus(ExportStatusEnum.SUCCESS.getCode());
        update.setFileUrl(fileUrl);
        update.setErrorMessage(null);
        exportJobMapper.updateById(update);
    }

    private void markFailed(Long exportJobId, String errorMessage) {
        ExportJob update = new ExportJob();
        update.setId(exportJobId);
        update.setStatus(ExportStatusEnum.FAILED.getCode());
        update.setErrorMessage(truncate(errorMessage, 500));
        exportJobMapper.updateById(update);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
