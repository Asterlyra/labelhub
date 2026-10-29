package com.labelhub.modules.dataset.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelhub.auth.util.AuthUtil;
import com.labelhub.common.constant.MinioBucketConstant;
import com.labelhub.common.constant.MinioObjectDirectoryConstant;
import com.labelhub.common.exception.BusinessException;
import com.labelhub.common.util.ExecutorLogUtil;
import com.labelhub.common.util.MinIOUtil;
import com.labelhub.modules.dataset.convert.DatasetConvert;
import com.labelhub.modules.dataset.dto.DatasetItemQueryDTO;
import com.labelhub.modules.dataset.dto.DatasetQueryDTO;
import com.labelhub.modules.dataset.dto.DatasetUploadDTO;
import com.labelhub.modules.dataset.entity.Dataset;
import com.labelhub.modules.dataset.entity.DatasetFile;
import com.labelhub.modules.dataset.entity.DatasetItem;
import com.labelhub.modules.dataset.enums.DatasetStatusEnum;
import com.labelhub.modules.dataset.enums.ParseStatusEnum;
import com.labelhub.modules.dataset.enums.SourceFormatEnum;
import com.labelhub.modules.dataset.mapper.DatasetFileMapper;
import com.labelhub.modules.dataset.mapper.DatasetItemMapper;
import com.labelhub.modules.dataset.mapper.DatasetMapper;
import com.labelhub.modules.dataset.service.DatasetParseService;
import com.labelhub.modules.dataset.service.DatasetService;
import com.labelhub.modules.dataset.service.impl.parser.FileParserFactory;
import com.labelhub.modules.dataset.service.impl.parser.ParseResult;
import com.labelhub.modules.dataset.service.impl.parser.ParsedItem;
import com.labelhub.modules.dataset.vo.DatasetDetailVO;
import com.labelhub.modules.dataset.vo.DatasetFieldPreviewVO;
import com.labelhub.modules.dataset.vo.DatasetFileVO;
import com.labelhub.modules.dataset.vo.DatasetItemVO;
import com.labelhub.modules.dataset.vo.DatasetVO;
import com.labelhub.modules.task.entity.TaskItem;
import com.labelhub.modules.task.mapper.TaskItemMapper;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.Executor;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据集服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatasetServiceImpl extends ServiceImpl<DatasetMapper, Dataset> implements DatasetService {

    private static final long DEFAULT_PAGE_NUM = 1L;
    private static final long DEFAULT_PAGE_SIZE = 10L;

    private final DatasetFileMapper datasetFileMapper;
    private final DatasetItemMapper datasetItemMapper;
    private final TaskItemMapper taskItemMapper;
    private final DatasetParseService datasetParseService;
    private final FileParserFactory fileParserFactory;
    private final DatasetConvert datasetConvert;
    private final ObjectMapper objectMapper;
    private final MinIOUtil minIOUtil;
    private final MinioClient minioClient;
    @Autowired
    @Qualifier("datasetParseExecutor")
    private Executor datasetParseExecutor;

    @Override
    @Transactional
    public DatasetVO uploadDataset(DatasetUploadDTO dto) {
        log.info("上传数据集开始：name={}", dto.getName());
        try {
            MultipartFile file = dto.getFile();
            validateUploadFile(file);
            SourceFormatEnum format = SourceFormatEnum.fromFilename(file.getOriginalFilename());
            String schemaJson = normalizeSchemaJson(dto.getSchema());
            String fileHash = computeSha256(file);
            MinIOUtil.UploadResult uploadResult = minIOUtil.upload(
                    file, MinioBucketConstant.DATASET, MinioObjectDirectoryConstant.DATASETS);

            Dataset dataset = datasetConvert.toEntity(dto);
            dataset.setSourceFormat(format.getCode());
            dataset.setStatus(DatasetStatusEnum.PARSING.getCode());
            dataset.setTotalCount(0);
            dataset.setSuccessCount(0);
            dataset.setFailedCount(0);
            dataset.setSchemaJson(schemaJson);
            dataset.setCreatedBy(AuthUtil.getCurrentUserId());
            save(dataset);

            DatasetFile datasetFile = new DatasetFile();
            datasetFile.setDatasetId(dataset.getId());
            datasetFile.setOriginalName(uploadResult.originalFilename());
            datasetFile.setFileUrl(uploadResult.url());
            datasetFile.setContentType(uploadResult.contentType());
            datasetFile.setFileSize(uploadResult.size());
            datasetFile.setFileHash(fileHash);
            datasetFile.setFormat(format.getCode());
            datasetFileMapper.insert(datasetFile);

            registerParseAfterCommit(dataset.getId(), format.getCode(), uploadResult.bucket(), uploadResult.objectName());
            log.info("上传数据集成功，已提交异步解析：datasetId={}", dataset.getId());
            return getDatasetVO(dataset.getId());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("上传数据集异常：{}", e.getMessage(), e);
            throw new BusinessException("上传数据集失败：" + e.getMessage());
        }
    }

    @Override
    public DatasetFieldPreviewVO previewFields(MultipartFile file) {
        log.info("预览数据集字段开始：filename={}", file == null ? null : file.getOriginalFilename());
        try {
            validateUploadFile(file);
            SourceFormatEnum format = SourceFormatEnum.fromFilename(file.getOriginalFilename());
            ParseResult result;
            try (InputStream inputStream = file.getInputStream()) {
                result = fileParserFactory.parse(format.getCode(), inputStream);
            }

            ParsedItem sampleItem = result.getItems().stream()
                    .filter(item -> ParseStatusEnum.SUCCESS.getCode().equals(item.getParseStatus()))
                    .filter(item -> item.getContent() != null && !item.getContent().isEmpty())
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("未解析到可预览字段"));

            DatasetFieldPreviewVO vo = new DatasetFieldPreviewVO();
            vo.setSourceFormat(format.getCode());
            for (Map.Entry<String, Object> entry : sampleItem.getContent().entrySet()) {
                DatasetFieldPreviewVO.FieldPreview field = new DatasetFieldPreviewVO.FieldPreview();
                field.setSourceName(entry.getKey());
                field.setSampleValue(entry.getValue());
                vo.getFields().add(field);
            }
            log.info("预览数据集字段成功：filename={}，fieldCount={}",
                    file.getOriginalFilename(), vo.getFields().size());
            return vo;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("预览数据集字段异常：{}", e.getMessage(), e);
            throw new BusinessException("预览数据集字段失败：" + e.getMessage());
        }
    }

    @Override
    public Page<DatasetVO> listDatasets(DatasetQueryDTO dto) {
        DatasetQueryDTO query = dto == null ? new DatasetQueryDTO() : dto;
        long pageNum = positiveOrDefault(query.getPageNum(), DEFAULT_PAGE_NUM);
        long pageSize = positiveOrDefault(query.getPageSize(), DEFAULT_PAGE_SIZE);
        log.info("查询数据集列表开始：keyword={}，status={}，format={}", query.getKeyword(), query.getStatus(), query.getSourceFormat());
        try {
            Page<Dataset> page = new Page<>(pageNum, pageSize);
            LambdaQueryWrapper<Dataset> wrapper = new LambdaQueryWrapper<Dataset>()
                    .like(StringUtils.hasText(query.getKeyword()), Dataset::getName, query.getKeyword())
                    .eq(StringUtils.hasText(query.getStatus()), Dataset::getStatus, query.getStatus())
                    .eq(StringUtils.hasText(query.getSourceFormat()), Dataset::getSourceFormat, query.getSourceFormat())
                    .orderByDesc(Dataset::getCreateTime);

            Page<Dataset> result = page(page, wrapper);
            Page<DatasetVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
            List<DatasetVO> records = result.getRecords().stream()
                    .map(this::toDatasetVO)
                    .toList();
            voPage.setRecords(records);
            log.info("查询数据集列表成功：total={}", result.getTotal());
            return voPage;
        } catch (Exception e) {
            log.error("查询数据集列表失败：{}", e.getMessage(), e);
            throw new BusinessException("查询数据集列表失败：" + e.getMessage());
        }
    }

    @Override
    public Page<DatasetVO> listMyDatasets(DatasetQueryDTO dto) {
        DatasetQueryDTO query = dto == null ? new DatasetQueryDTO() : dto;
        long pageNum = positiveOrDefault(query.getPageNum(), DEFAULT_PAGE_NUM);
        long pageSize = positiveOrDefault(query.getPageSize(), DEFAULT_PAGE_SIZE);
        Long currentUserId = AuthUtil.getCurrentUserId();
        log.info("查询我的数据集列表开始：userId={}，keyword={}，status={}，format={}",
                currentUserId, query.getKeyword(), query.getStatus(), query.getSourceFormat());
        try {
            Page<Dataset> page = new Page<>(pageNum, pageSize);
            LambdaQueryWrapper<Dataset> wrapper = new LambdaQueryWrapper<Dataset>()
                    .eq(Dataset::getCreatedBy, currentUserId)
                    .like(StringUtils.hasText(query.getKeyword()), Dataset::getName, query.getKeyword())
                    .eq(StringUtils.hasText(query.getStatus()), Dataset::getStatus, query.getStatus())
                    .eq(StringUtils.hasText(query.getSourceFormat()), Dataset::getSourceFormat, query.getSourceFormat())
                    .orderByDesc(Dataset::getCreateTime);

            Page<Dataset> result = page(page, wrapper);
            Page<DatasetVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
            List<DatasetVO> records = result.getRecords().stream()
                    .map(this::toDatasetVO)
                    .toList();
            voPage.setRecords(records);
            log.info("查询我的数据集列表成功：userId={}，total={}", currentUserId, result.getTotal());
            return voPage;
        } catch (Exception e) {
            log.error("查询我的数据集列表失败：userId={}，原因={}", currentUserId, e.getMessage(), e);
            throw new BusinessException("查询我的数据集列表失败：" + e.getMessage());
        }
    }

    @Override
    public DatasetDetailVO getDatasetDetail(Long id) {
        log.info("查询数据集详情开始：datasetId={}", id);
        try {
            Dataset dataset = getRequiredDataset(id);
            DatasetDetailVO vo = datasetConvert.toDetailVO(dataset);
            fillSchema(vo, dataset.getSchemaJson());
            List<DatasetFile> files = datasetFileMapper.selectList(
                    new LambdaQueryWrapper<DatasetFile>().eq(DatasetFile::getDatasetId, id));
            List<DatasetFileVO> fileVOs = datasetConvert.toFileVOList(files);
            vo.setFiles(fileVOs);
            log.info("查询数据集详情成功：datasetId={}", id);
            return vo;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("查询数据集详情异常：{}", e.getMessage(), e);
            throw new BusinessException("查询数据集详情失败：" + e.getMessage());
        }
    }

    @Override
    public Page<DatasetItemVO> listDatasetItems(Long datasetId, DatasetItemQueryDTO dto) {
        DatasetItemQueryDTO query = dto == null ? new DatasetItemQueryDTO() : dto;
        long pageNum = positiveOrDefault(query.getPageNum(), DEFAULT_PAGE_NUM);
        long pageSize = positiveOrDefault(query.getPageSize(), DEFAULT_PAGE_SIZE);
        log.info("查询数据项列表开始：datasetId={}", datasetId);
        try {
            getRequiredDataset(datasetId);
            Page<DatasetItem> page = new Page<>(pageNum, pageSize);
            LambdaQueryWrapper<DatasetItem> wrapper = new LambdaQueryWrapper<DatasetItem>()
                    .eq(DatasetItem::getDatasetId, datasetId)
                    .eq(StringUtils.hasText(query.getParseStatus()), DatasetItem::getParseStatus, query.getParseStatus())
                    .eq(StringUtils.hasText(query.getDataType()), DatasetItem::getDataType, query.getDataType())
                    .and(StringUtils.hasText(query.getKeyword()), item -> item
                            .like(DatasetItem::getItemKey, query.getKeyword())
                            .or()
                            .like(DatasetItem::getContentJson, query.getKeyword()))
                    .orderByAsc(DatasetItem::getRowIndex)
                    .orderByAsc(DatasetItem::getId);

            Page<DatasetItem> result = datasetItemMapper.selectPage(page, wrapper);
            Page<DatasetItemVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
            List<DatasetItemVO> records = result.getRecords().stream()
                    .map(this::toDatasetItemVO)
                    .toList();
            voPage.setRecords(records);
            log.info("查询数据项列表成功：datasetId={}，total={}", datasetId, result.getTotal());
            return voPage;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("查询数据项列表异常：{}", e.getMessage(), e);
            throw new BusinessException("查询数据项列表失败：" + e.getMessage());
        }
    }

    @Override
    @Transactional
    public void deleteDataset(Long id) {
        log.info("删除数据集开始：datasetId={}", id);
        try {
            getRequiredDataset(id);
            List<DatasetItem> items = datasetItemMapper.selectList(new LambdaQueryWrapper<DatasetItem>()
                    .select(DatasetItem::getId)
                    .eq(DatasetItem::getDatasetId, id));
            List<Long> itemIds = items.stream().map(DatasetItem::getId).toList();
            if (!itemIds.isEmpty()) {
                Long referencedCount = taskItemMapper.selectCount(
                        new LambdaQueryWrapper<TaskItem>().in(TaskItem::getDatasetItemId, itemIds));
                if (referencedCount != null && referencedCount > 0) {
                    throw new BusinessException("数据集已被任务引用，不能删除");
                }
            }

            removeById(id);
            datasetItemMapper.delete(new LambdaQueryWrapper<DatasetItem>().eq(DatasetItem::getDatasetId, id));
            List<DatasetFile> files = datasetFileMapper.selectList(
                    new LambdaQueryWrapper<DatasetFile>().eq(DatasetFile::getDatasetId, id));
            for (DatasetFile file : files) {
                deleteMinioObject(file.getFileUrl());
            }
            datasetFileMapper.delete(new LambdaQueryWrapper<DatasetFile>().eq(DatasetFile::getDatasetId, id));
            log.info("删除数据集成功：datasetId={}", id);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("删除数据集异常：{}", e.getMessage(), e);
            throw new BusinessException("删除数据集失败：" + e.getMessage());
        }
    }

    private void registerParseAfterCommit(Long datasetId, String format, String bucket, String objectName) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    log.info("提交数据集异步解析任务：datasetId={}，format={}，executor={}",
                            datasetId, format, ExecutorLogUtil.snapshot(datasetParseExecutor));
                    datasetParseService.parseDatasetFileAsync(datasetId, format, bucket, objectName, true);
                    log.info("数据集异步解析任务已提交：datasetId={}，executor={}",
                            datasetId, ExecutorLogUtil.snapshot(datasetParseExecutor));
                }
            });
            return;
        }
        log.info("提交数据集异步解析任务：datasetId={}，format={}，executor={}",
                datasetId, format, ExecutorLogUtil.snapshot(datasetParseExecutor));
        datasetParseService.parseDatasetFileAsync(datasetId, format, bucket, objectName, true);
        log.info("数据集异步解析任务已提交：datasetId={}，executor={}",
                datasetId, ExecutorLogUtil.snapshot(datasetParseExecutor));
    }

    private void validateUploadFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }
        SourceFormatEnum.fromFilename(file.getOriginalFilename());
    }

    /**
     * 校验并规范化字段映射 schema。
     */
    private String normalizeSchemaJson(String schema) {
        if (!StringUtils.hasText(schema)) {
            throw new BusinessException("字段映射 schema 不能为空，请先预览并确认字段映射");
        }
        try {
            Map<String, Object> root = objectMapper.readValue(schema, new TypeReference<LinkedHashMap<String, Object>>() {
            });
            Object fieldsValue = root.get("fields");
            if (!(fieldsValue instanceof List<?> rawFields) || rawFields.isEmpty()) {
                throw new BusinessException("schema.fields不能为空");
            }

            List<Map<String, Object>> fields = new java.util.ArrayList<>();
            java.util.Set<String> enabledTargetNames = new java.util.LinkedHashSet<>();
            int enabledCount = 0;
            for (Object rawField : rawFields) {
                if (!(rawField instanceof Map<?, ?> fieldMap)) {
                    throw new BusinessException("schema.fields格式不正确");
                }

                String sourceName = readString(fieldMap.get("sourceName"));
                boolean enabled = readBoolean(fieldMap.get("enabled"), true);
                String targetName = readString(fieldMap.get("targetName"));
                if (!StringUtils.hasText(sourceName)) {
                    throw new BusinessException("sourceName不能为空");
                }
                if (enabled && !StringUtils.hasText(targetName)) {
                    throw new BusinessException("启用字段的targetName不能为空");
                }
                if (enabled && !enabledTargetNames.add(targetName)) {
                    throw new BusinessException("targetName不能重复：" + targetName);
                }
                if (enabled) {
                    enabledCount++;
                }

                Map<String, Object> field = new LinkedHashMap<>();
                field.put("sourceName", sourceName);
                field.put("targetName", StringUtils.hasText(targetName) ? targetName : sourceName);
                field.put("enabled", enabled);
                fields.add(field);
            }
            if (enabledCount == 0) {
                throw new BusinessException("至少需要启用一个字段");
            }

            Map<String, Object> normalized = new LinkedHashMap<>();
            normalized.put("fields", fields);
            return objectMapper.writeValueAsString(normalized);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("字段映射 schema 解析失败：" + e.getMessage());
        }
    }

    private String readString(Object value) {
        if (value == null) {
            return "";
        }
        return String.valueOf(value).trim();
    }

    private boolean readBoolean(Object value, boolean fallback) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof Number numberValue) {
            return numberValue.intValue() != 0;
        }
        if (value instanceof String stringValue) {
            if ("true".equalsIgnoreCase(stringValue) || "1".equals(stringValue)) {
                return true;
            }
            if ("false".equalsIgnoreCase(stringValue) || "0".equals(stringValue)) {
                return false;
            }
        }
        return fallback;
    }

    private String computeSha256(MultipartFile file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream inputStream = file.getInputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return toHex(digest.digest());
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }

    private Dataset getRequiredDataset(Long id) {
        Dataset dataset = getById(id);
        if (dataset == null) {
            throw new BusinessException("数据集不存在");
        }
        return dataset;
    }

    private DatasetVO getDatasetVO(Long id) {
        return toDatasetVO(getRequiredDataset(id));
    }

    private DatasetVO toDatasetVO(Dataset dataset) {
        DatasetVO vo = datasetConvert.toVO(dataset);
        fillSchema(vo, dataset == null ? null : dataset.getSchemaJson());
        return vo;
    }

    private void fillSchema(DatasetVO vo, String schemaJson) {
        vo.setSchema(parseJsonMap(schemaJson));
    }

    private DatasetItemVO toDatasetItemVO(DatasetItem item) {
        DatasetItemVO vo = datasetConvert.toItemVO(item);
        vo.setContent(parseJsonMap(item == null ? null : item.getContentJson()));
        return vo;
    }

    private Map<String, Object> parseJsonMap(String json) {
        if (!StringUtils.hasText(json)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (Exception e) {
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("raw", json);
            return fallback;
        }
    }

    private void deleteMinioObject(String fileUrl) {
        StorageLocation location = parseFileUrl(fileUrl);
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(location.bucket())
                    .object(location.objectName())
                    .build());
        } catch (Exception e) {
            log.warn("删除 MinIO 文件失败：fileUrl={}，原因={}", fileUrl, e.getMessage());
        }
    }

    private StorageLocation parseFileUrl(String fileUrl) {
        if (!StringUtils.hasText(fileUrl)) {
            throw new BusinessException("MinIO 文件 URL 不能为空");
        }
        try {
            String path = URI.create(fileUrl).getPath();
            String normalizedPath = path == null ? "" : path.replaceAll("^/+", "");
            int slashIndex = normalizedPath.indexOf('/');
            if (slashIndex < 0 || slashIndex == normalizedPath.length() - 1) {
                throw new BusinessException("MinIO 文件 URL 不合法：" + fileUrl);
            }
            String bucket = normalizedPath.substring(0, slashIndex);
            String objectName = URLDecoder.decode(normalizedPath.substring(slashIndex + 1), StandardCharsets.UTF_8);
            return new StorageLocation(bucket, objectName);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("MinIO 文件 URL 不合法：" + fileUrl);
        }
    }

    private long positiveOrDefault(Integer value, long defaultValue) {
        return value == null || value <= 0 ? defaultValue : value;
    }

    private record StorageLocation(String bucket, String objectName) {
    }
}
