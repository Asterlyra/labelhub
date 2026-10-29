package com.labelhub.modules.export.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelhub.auth.util.AuthUtil;
import com.labelhub.common.exception.BusinessException;
import com.labelhub.modules.dataset.entity.DatasetItem;
import com.labelhub.modules.dataset.mapper.DatasetItemMapper;
import com.labelhub.modules.export.dto.ExportCreateDTO;
import com.labelhub.modules.export.dto.ExportFieldMappingDTO;
import com.labelhub.modules.export.entity.ExportJob;
import com.labelhub.modules.export.enums.ExportFormatEnum;
import com.labelhub.modules.export.enums.ExportStatusEnum;
import com.labelhub.modules.export.mapper.ExportJobMapper;
import com.labelhub.modules.export.service.ExportAsyncService;
import com.labelhub.modules.export.service.ExportService;
import com.labelhub.modules.export.service.ExportService.ExportDownloadFile;
import com.labelhub.modules.export.vo.ExportFieldPreviewVO;
import com.labelhub.modules.export.vo.ExportJobVO;
import com.labelhub.modules.task.entity.Task;
import com.labelhub.modules.task.entity.TaskItem;
import com.labelhub.modules.task.enums.TaskItemStatusEnum;
import com.labelhub.modules.task.enums.TaskStatusEnum;
import com.labelhub.modules.task.mapper.TaskItemMapper;
import com.labelhub.modules.task.mapper.TaskMapper;
import com.labelhub.modules.template.entity.Material;
import com.labelhub.modules.template.mapper.MaterialMapper;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExportServiceImpl implements ExportService {

    private static final String LLM_INTERACTION_TYPE = "llmInteraction";

    private final TaskMapper taskMapper;
    private final TaskItemMapper taskItemMapper;
    private final DatasetItemMapper datasetItemMapper;
    private final MaterialMapper materialMapper;
    private final ExportJobMapper exportJobMapper;
    private final ExportAsyncService exportAsyncService;
    private final ObjectMapper objectMapper;
    private final MinioClient minioClient;

    @Override
    public ExportJobVO createExport(Long taskId, ExportCreateDTO dto) {
        Task task = getOwnedTask(taskId);
        if (!TaskStatusEnum.ENDED.getCode().equals(task.getStatus())) {
            throw new BusinessException("只有已结束的任务支持导出");
        }

        ExportFormatEnum format = ExportFormatEnum.fromCode(dto.getFormat());
        List<ExportFieldMappingDTO> fields = normalizeFields(dto.getFields());

        ExportJob job = new ExportJob();
        job.setTaskId(taskId);
        job.setFormat(format.getCode());
        job.setStatus(ExportStatusEnum.PENDING.getCode());
        job.setConfigJson(writeConfigJson(fields));
        job.setCreatedBy(AuthUtil.getCurrentUserId());
        exportJobMapper.insert(job);

        exportAsyncService.execute(job.getId());
        return toVO(job);
    }

    @Override
    public List<ExportFieldPreviewVO> previewFields(Long taskId) {
        Task task = getOwnedTask(taskId);
        if (!TaskStatusEnum.ENDED.getCode().equals(task.getStatus())) {
            throw new BusinessException("只有已结束的任务支持预览导出字段");
        }

        List<TaskItem> taskItems = taskItemMapper.selectList(new LambdaQueryWrapper<TaskItem>()
                .eq(TaskItem::getTaskId, taskId)
                .eq(TaskItem::getStatus, TaskItemStatusEnum.REVIEWED.getCode())
                .orderByAsc(TaskItem::getId)
                .last("LIMIT 20"));
        if (taskItems.isEmpty()) {
            throw new BusinessException("当前任务没有可预览的审核通过数据");
        }

        Map<String, Object> rawSamples = new LinkedHashMap<>();
        Map<String, Object> annotationSamples = new LinkedHashMap<>();
        Map<Long, DatasetItem> datasetItemMap = loadDatasetItems(taskItems);
        for (TaskItem taskItem : taskItems) {
            DatasetItem datasetItem = datasetItemMap.get(taskItem.getDatasetItemId());
            if (datasetItem != null) {
                collectSamples(rawSamples, parseJsonObject(datasetItem.getContentJson(), "数据集内容"));
            }
            collectSamples(annotationSamples, parseJsonObject(taskItem.getAnnotationData(), "标注结果"));
        }

        List<Material> materials = materialMapper.selectByTemplateId(task.getTemplateId());
        Set<String> skippedFields = materials.stream()
                .filter(this::shouldSkipExportMaterial)
                .map(Material::getField)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        skippedFields.forEach(rawSamples::remove);
        skippedFields.forEach(annotationSamples::remove);

        Map<String, Object> samples = new LinkedHashMap<>(rawSamples);
        for (Material material : materials) {
            if (shouldSkipExportMaterial(material) || !StringUtils.hasText(material.getField())) {
                continue;
            }
            String field = material.getField();
            Object sampleValue = annotationSamples.containsKey(field) ? annotationSamples.get(field) : samples.get(field);
            samples.put(field, sampleValue);
        }

        return samples.entrySet().stream()
                .map(entry -> {
                    ExportFieldPreviewVO vo = new ExportFieldPreviewVO();
                    vo.setSourceName(entry.getKey());
                    vo.setSampleValue(entry.getValue());
                    return vo;
                })
                .toList();
    }

    private void collectSamples(Map<String, Object> samples, Map<String, Object> source) {
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            samples.putIfAbsent(entry.getKey(), entry.getValue());
            if (samples.get(entry.getKey()) == null && entry.getValue() != null) {
                samples.put(entry.getKey(), entry.getValue());
            }
        }
    }

    private boolean shouldSkipExportMaterial(Material material) {
        return LLM_INTERACTION_TYPE.equals(material.getType());
    }

    @Override
    public List<ExportJobVO> listTaskExports(Long taskId) {
        getOwnedTask(taskId);
        return exportJobMapper.selectList(new LambdaQueryWrapper<ExportJob>()
                        .eq(ExportJob::getTaskId, taskId)
                        .orderByDesc(ExportJob::getCreateTime)
                        .orderByDesc(ExportJob::getId))
                .stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    public ExportJobVO getExport(Long exportId) {
        ExportJob job = exportJobMapper.selectById(exportId);
        if (job == null) {
            throw new BusinessException("导出任务不存在");
        }
        getOwnedTask(job.getTaskId());
        return toVO(job);
    }

    @Override
    public ExportDownloadFile downloadExport(Long exportId) {
        ExportJob job = exportJobMapper.selectById(exportId);
        if (job == null) {
            throw new BusinessException("导出任务不存在");
        }
        getOwnedTask(job.getTaskId());
        if (!ExportStatusEnum.SUCCESS.getCode().equals(job.getStatus())) {
            throw new BusinessException("导出任务尚未成功完成");
        }
        if (!StringUtils.hasText(job.getFileUrl())) {
            throw new BusinessException("导出文件地址不存在");
        }

        StorageLocation location = parseFileUrl(job.getFileUrl());
        try {
            InputStream inputStream = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(location.bucket())
                    .object(location.objectName())
                    .build());
            ExportFormatEnum format = ExportFormatEnum.fromCode(job.getFormat());
            return new ExportDownloadFile(inputStream, resolveDownloadFilename(job, location), format.getContentType());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("读取导出文件失败：" + e.getMessage());
        }
    }

    private Task getOwnedTask(Long taskId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("任务不存在");
        }
        Long currentUserId = AuthUtil.getCurrentUserId();
        if (!currentUserId.equals(task.getCreatorId())) {
            throw new BusinessException("无权访问该任务的导出数据");
        }
        return task;
    }

    private Map<Long, DatasetItem> loadDatasetItems(List<TaskItem> taskItems) {
        Collection<Long> datasetItemIds = taskItems.stream()
                .map(TaskItem::getDatasetItemId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (datasetItemIds.isEmpty()) {
            throw new BusinessException("任务项未关联数据集数据");
        }
        return datasetItemMapper.selectBatchIds(datasetItemIds).stream()
                .collect(Collectors.toMap(DatasetItem::getId, Function.identity(), (left, right) -> left));
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

    private List<ExportFieldMappingDTO> normalizeFields(List<ExportFieldMappingDTO> fields) {
        if (fields == null || fields.isEmpty()) {
            throw new BusinessException("导出字段不能为空");
        }

        List<ExportFieldMappingDTO> normalized = new ArrayList<>();
        Set<String> targetNames = new LinkedHashSet<>();
        for (ExportFieldMappingDTO field : fields) {
            if (field == null || Boolean.FALSE.equals(field.getEnabled())) {
                continue;
            }
            String sourceName = trimToNull(field.getSourceName());
            String targetName = trimToNull(field.getTargetName());
            if (!StringUtils.hasText(sourceName)) {
                throw new BusinessException("导出源字段不能为空");
            }
            if (!StringUtils.hasText(targetName)) {
                throw new BusinessException("导出目标字段不能为空");
            }
            if (!targetNames.add(targetName)) {
                throw new BusinessException("导出目标字段不能重复：" + targetName);
            }
            ExportFieldMappingDTO normalizedField = new ExportFieldMappingDTO();
            normalizedField.setSourceName(sourceName);
            normalizedField.setTargetName(targetName);
            normalizedField.setEnabled(true);
            normalized.add(normalizedField);
        }
        if (normalized.isEmpty()) {
            throw new BusinessException("至少需要选择一个导出字段");
        }
        return normalized;
    }

    private String trimToNull(String value) {
        return value == null ? null : value.trim();
    }

    private String writeConfigJson(List<ExportFieldMappingDTO> fields) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("fields", fields);
        try {
            return objectMapper.writeValueAsString(config);
        } catch (JsonProcessingException e) {
            throw new BusinessException("导出配置序列化失败：" + e.getMessage());
        }
    }

    private ExportJobVO toVO(ExportJob job) {
        ExportJobVO vo = new ExportJobVO();
        vo.setId(job.getId());
        vo.setTaskId(job.getTaskId());
        vo.setFormat(job.getFormat());
        vo.setStatus(job.getStatus());
        vo.setFileUrl(job.getFileUrl());
        vo.setDownloadUrl(buildDownloadUrl(job));
        vo.setErrorMessage(job.getErrorMessage());
        vo.setConfig(parseConfig(job.getConfigJson()));
        vo.setCreatedBy(job.getCreatedBy());
        vo.setCreateTime(job.getCreateTime());
        vo.setUpdateTime(job.getUpdateTime());
        return vo;
    }

    private Map<String, Object> parseConfig(String configJson) {
        if (!StringUtils.hasText(configJson)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(configJson, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (JsonProcessingException e) {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("fields", List.of());
            return config;
        }
    }

    private String buildDownloadUrl(ExportJob job) {
        if (job == null || job.getId() == null || !StringUtils.hasText(job.getFileUrl())) {
            return null;
        }
        return "/api/exports/" + job.getId() + "/download";
    }

    private StorageLocation parseFileUrl(String fileUrl) {
        try {
            String path = URI.create(fileUrl).getPath();
            String[] parts = path.split("/", 3);
            if (parts.length < 3 || !StringUtils.hasText(parts[1]) || !StringUtils.hasText(parts[2])) {
                throw new BusinessException("导出文件 URL 不合法：" + fileUrl);
            }
            return new StorageLocation(parts[1], URLDecoder.decode(parts[2], StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            throw new BusinessException("导出文件 URL 不合法：" + fileUrl);
        }
    }

    private String resolveDownloadFilename(ExportJob job, StorageLocation location) {
        String objectName = location.objectName();
        int slashIndex = objectName.lastIndexOf('/');
        String filename = slashIndex >= 0 ? objectName.substring(slashIndex + 1) : objectName;
        if (StringUtils.hasText(filename)) {
            return filename;
        }
        ExportFormatEnum format = ExportFormatEnum.fromCode(job.getFormat());
        return "task-" + job.getTaskId() + "-export-" + job.getId() + format.getExtension();
    }

    private record StorageLocation(String bucket, String objectName) {
    }
}
