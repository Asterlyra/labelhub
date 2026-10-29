package com.labelhub.modules.dataset.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelhub.common.exception.BusinessException;
import com.labelhub.common.util.ExecutorLogUtil;
import com.labelhub.modules.dataset.entity.Dataset;
import com.labelhub.modules.dataset.entity.DatasetItem;
import com.labelhub.modules.dataset.enums.DatasetStatusEnum;
import com.labelhub.modules.dataset.enums.ParseStatusEnum;
import com.labelhub.modules.dataset.mapper.DatasetItemMapper;
import com.labelhub.modules.dataset.mapper.DatasetMapper;
import com.labelhub.modules.dataset.service.DatasetParseService;
import com.labelhub.modules.dataset.service.impl.parser.DataTypeDetector;
import com.labelhub.modules.dataset.service.impl.parser.FileParserFactory;
import com.labelhub.modules.dataset.service.impl.parser.ParseResult;
import com.labelhub.modules.dataset.service.impl.parser.ParsedItem;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * 数据集解析服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatasetParseServiceImpl extends ServiceImpl<DatasetItemMapper, DatasetItem> implements DatasetParseService {

    private static final int BATCH_SIZE = 1000;
    private static final int SCHEMA_SAMPLE_SIZE = 100;

    private final DatasetMapper datasetMapper;
    private final DatasetItemMapper datasetItemMapper;
    private final FileParserFactory fileParserFactory;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager transactionManager;
    private final MinioClient minioClient;
    @Autowired
    @Qualifier("datasetParseExecutor")
    private Executor datasetParseExecutor;

    @Override
    public void parseDatasetFile(Long datasetId, String format, InputStream inputStream, boolean clearOldItems) {
        long startNanos = System.nanoTime();
        log.info("解析数据集开始：datasetId={}，format={}，clearOldItems={}",
                datasetId, format, clearOldItems);
        try {
            Dataset dataset = datasetMapper.selectById(datasetId);
            if (dataset == null) {
                throw new BusinessException("数据集不存在");
            }

            runInTransaction(() -> updateStatus(datasetId, DatasetStatusEnum.PARSING.getCode()));
            if (clearOldItems) {
                int deletedCount = runInTransaction(() -> datasetItemMapper.delete(new LambdaQueryWrapper<DatasetItem>()
                        .eq(DatasetItem::getDatasetId, datasetId)));
                log.info("清理数据集历史解析项：datasetId={}，deletedCount={}", datasetId, deletedCount);
            }

            List<FieldMapping> mappings = readFieldMappings(dataset.getSchemaJson());
            ParseAccumulator accumulator = new ParseAccumulator();
            FieldMappingStats mappingStats = mappings.isEmpty() ? null : new FieldMappingStats(mappings);

            log.info("数据集使用分批解析模式：datasetId={}，format={}，batchSize={}，mappingCount={}",
                    datasetId, format, BATCH_SIZE, mappings.size());
            int[] batchNo = {0};
            fileParserFactory.parseBatches(format, inputStream, BATCH_SIZE, batch -> {
                batchNo[0]++;
                List<ParsedItem> parsedBatch = applyFieldMappings(batch, mappings, mappingStats);
                persistParsedBatch(datasetId, parsedBatch, accumulator, batchNo[0], false);
            });

            runInTransaction(() -> updateParseSummary(datasetId, accumulator, mappings.isEmpty()));
            logMappingStats(datasetId, mappingStats, accumulator.successCount);
            log.info("解析数据集成功：datasetId={}，total={}，successCount={}，failedCount={}，batchSize={}，schemaSampleSize={}，costMs={}",
                    datasetId, accumulator.totalCount, accumulator.successCount, accumulator.failedCount,
                    BATCH_SIZE, accumulator.schemaSamples.size(),
                    elapsedMillis(startNanos));
        } catch (Exception e) {
            log.error("解析数据集失败：datasetId={}，原因={}", datasetId, e.getMessage(), e);
            runInTransaction(() -> markParseFailed(datasetId, e.getMessage()));
            log.info("解析数据集失败状态已更新：datasetId={}，costMs={}", datasetId, elapsedMillis(startNanos));
        }
    }

    private void persistParsedBatch(Long datasetId, List<ParsedItem> parsedBatch, ParseAccumulator accumulator,
                                    int batchNo, boolean maybeLastBatch) {
        if (parsedBatch.isEmpty()) {
            return;
        }
        List<DatasetItem> datasetItems = parsedBatch.stream()
                .map(parsedItem -> toDatasetItem(datasetId, parsedItem))
                .toList();
        runInTransaction(() -> datasetItemMapper.insertBatch(datasetItems));
        accumulator.add(parsedBatch);
        if (batchNo == 1 || batchNo % 10 == 0 || maybeLastBatch) {
            log.info("数据集解析批次入库完成：datasetId={}，batchNo={}，batchSize={}，totalProcessed={}，successCount={}，failedCount={}",
                    datasetId, batchNo, parsedBatch.size(), accumulator.totalCount,
                    accumulator.successCount, accumulator.failedCount);
        }
    }

    @Override
    @Async("datasetParseExecutor")
    public void parseDatasetFileAsync(Long datasetId, String format, String bucket, String objectName, boolean clearOldItems) {
        long startNanos = System.nanoTime();
        String threadName = Thread.currentThread().getName();
        log.info("数据集异步解析任务开始：datasetId={}，format={}，bucket={}，objectName={}，thread={}，executor={}",
                datasetId, format, bucket, objectName, threadName, ExecutorLogUtil.snapshot(datasetParseExecutor));
        try (InputStream inputStream = minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucket)
                .object(objectName)
                .build())) {
            log.info("数据集异步解析 MinIO 文件流打开成功：datasetId={}，thread={}", datasetId, threadName);
            parseDatasetFile(datasetId, format, inputStream, clearOldItems);
        } catch (Exception e) {
            log.error("异步解析数据集失败：datasetId={}，原因={}", datasetId, e.getMessage(), e);
            runInTransaction(() -> markParseFailed(datasetId, e.getMessage()));
        } finally {
            log.info("数据集异步解析任务结束：datasetId={}，thread={}，costMs={}，executor={}",
                    datasetId, threadName, elapsedMillis(startNanos), ExecutorLogUtil.snapshot(datasetParseExecutor));
        }
    }

    private DatasetItem toDatasetItem(Long datasetId, ParsedItem parsedItem) {
        DatasetItem item = new DatasetItem();
        item.setDatasetId(datasetId);
        item.setItemKey(parsedItem.getRowIndex() == null ? null : String.valueOf(parsedItem.getRowIndex()));
        item.setRowIndex(parsedItem.getRowIndex());
        item.setDataType(parsedItem.getDataType());
        item.setParseStatus(parsedItem.getParseStatus());
        item.setErrorMessage(parsedItem.getErrorMessage());
        item.setContentJson(toJson(parsedItem.getContent()));
        return item;
    }

    private void updateStatus(Long datasetId, String status) {
        Dataset update = new Dataset();
        update.setId(datasetId);
        update.setStatus(status);
        datasetMapper.updateById(update);
    }

    private void updateParseSummary(Long datasetId, ParseAccumulator accumulator, boolean shouldBuildSchema) {
        Dataset update = new Dataset();
        update.setId(datasetId);
        update.setStatus(accumulator.failedCount > 0 && accumulator.successCount == 0
                ? DatasetStatusEnum.FAILED.getCode()
                : DatasetStatusEnum.PARSED.getCode());
        update.setTotalCount(accumulator.totalCount);
        update.setSuccessCount(accumulator.successCount);
        update.setFailedCount(accumulator.failedCount);
        if (shouldBuildSchema) {
            update.setSchemaJson(toJson(buildSchema(accumulator.schemaSamples)));
        }
        datasetMapper.updateById(update);
    }

    private List<ParsedItem> applyFieldMappings(List<ParsedItem> items, List<FieldMapping> mappings,
                                                FieldMappingStats mappingStats) {
        if (mappings.isEmpty()) {
            return items;
        }
        List<ParsedItem> mappedItems = new ArrayList<>();
        for (ParsedItem item : items) {
            if (!ParseStatusEnum.SUCCESS.getCode().equals(item.getParseStatus()) || item.getContent() == null) {
                mappedItems.add(item);
                continue;
            }

            Map<String, Object> mappedContent = new LinkedHashMap<>();
            for (FieldMapping mapping : mappings) {
                Object value = item.getContent().get(mapping.sourceName());
                mappedContent.put(mapping.targetName(), value);
                if (value != null) {
                    mappingStats.recordHit(mapping);
                }
            }

            ParsedItem mappedItem = new ParsedItem();
            mappedItem.setItemKey(item.getItemKey());
            mappedItem.setRowIndex(item.getRowIndex());
            mappedItem.setContent(mappedContent);
            mappedItem.setDataType(DataTypeDetector.detect(mappedContent));
            mappedItem.setParseStatus(item.getParseStatus());
            mappedItem.setErrorMessage(item.getErrorMessage());
            mappedItems.add(mappedItem);
        }
        return mappedItems;
    }

    private List<FieldMapping> readFieldMappings(String schemaJson) {
        if (!StringUtils.hasText(schemaJson)) {
            return List.of();
        }
        try {
            Map<String, Object> root = objectMapper.readValue(schemaJson, new TypeReference<LinkedHashMap<String, Object>>() {
            });
            Object fieldsValue = root.get("fields");
            if (!(fieldsValue instanceof List<?> rawFields)) {
                return List.of();
            }

            List<FieldMapping> mappings = new ArrayList<>();
            for (Object rawField : rawFields) {
                if (!(rawField instanceof Map<?, ?> fieldMap)) {
                    continue;
                }
                boolean enabled = readBoolean(fieldMap.get("enabled"), true);
                if (!enabled) {
                    continue;
                }
                String sourceName = readString(fieldMap.get("sourceName"));
                String targetName = readString(fieldMap.get("targetName"));
                if (StringUtils.hasText(sourceName) && StringUtils.hasText(targetName)) {
                    mappings.add(new FieldMapping(sourceName, targetName));
                }
            }
            return mappings;
        } catch (Exception e) {
            throw new BusinessException("字段映射 schema 解析失败：" + e.getMessage());
        }
    }

    private String readString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
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

    private Map<String, Object> buildSchema(List<ParsedItem> items) {
        Set<String> names = new LinkedHashSet<>();
        Map<String, Object> sampleTypes = new LinkedHashMap<>();
        for (ParsedItem item : items) {
            if (!ParseStatusEnum.SUCCESS.getCode().equals(item.getParseStatus()) || item.getContent() == null) {
                continue;
            }
            for (Map.Entry<String, Object> entry : item.getContent().entrySet()) {
                names.add(entry.getKey());
                sampleTypes.putIfAbsent(entry.getKey(), entry.getValue() == null
                        ? "null"
                        : entry.getValue().getClass().getSimpleName());
            }
        }

        List<Map<String, Object>> fields = new ArrayList<>();
        for (String name : names) {
            Map<String, Object> field = new LinkedHashMap<>();
            field.put("name", name);
            field.put("sampleType", sampleTypes.get(name));
            fields.add(field);
        }
        return Map.of("fields", fields);
    }

    private void markParseFailed(Long datasetId, String message) {
        Dataset update = new Dataset();
        update.setId(datasetId);
        update.setStatus(DatasetStatusEnum.FAILED.getCode());
        update.setFailedCount(1);
        Dataset dataset = datasetMapper.selectById(datasetId);
        if (dataset == null || !StringUtils.hasText(dataset.getSchemaJson())) {
            update.setSchemaJson(toJson(Map.of("error", message)));
        }
        datasetMapper.updateById(update);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception e) {
            throw new BusinessException("JSON 序列化失败：" + e.getMessage());
        }
    }

    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    private void logMappingStats(Long datasetId, FieldMappingStats mappingStats, int successCount) {
        if (mappingStats == null) {
            return;
        }
        for (FieldMapping mapping : mappingStats.mappings) {
            int hitCount = mappingStats.hitCounts.getOrDefault(mapping.sourceName(), 0);
            if (hitCount == 0) {
                log.warn("字段映射未命中任何非空值：datasetId={}，sourceName={}，targetName={}，successCount={}",
                        datasetId, mapping.sourceName(), mapping.targetName(), successCount);
                continue;
            }
            log.info("字段映射命中统计：datasetId={}，sourceName={}，targetName={}，hitCount={}，successCount={}",
                    datasetId, mapping.sourceName(), mapping.targetName(), hitCount, successCount);
        }
    }

    private void runInTransaction(Runnable action) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> action.run());
    }

    private <T> T runInTransaction(TransactionAction<T> action) {
        return new TransactionTemplate(transactionManager).execute(status -> action.execute());
    }

    private record FieldMapping(String sourceName, String targetName) {
    }

    private interface TransactionAction<T> {
        T execute();
    }

    private static class FieldMappingStats {

        private final List<FieldMapping> mappings;
        private final Map<String, Integer> hitCounts = new LinkedHashMap<>();

        private FieldMappingStats(List<FieldMapping> mappings) {
            this.mappings = mappings;
            for (FieldMapping mapping : mappings) {
                hitCounts.put(mapping.sourceName(), 0);
            }
        }

        private void recordHit(FieldMapping mapping) {
            hitCounts.computeIfPresent(mapping.sourceName(), (key, count) -> count + 1);
        }
    }

    private static class ParseAccumulator {

        private int totalCount;
        private int successCount;
        private int failedCount;
        private final List<ParsedItem> schemaSamples = new ArrayList<>();

        private void add(List<ParsedItem> items) {
            for (ParsedItem item : items) {
                totalCount++;
                if (ParseStatusEnum.SUCCESS.getCode().equals(item.getParseStatus())) {
                    successCount++;
                    if (schemaSamples.size() < SCHEMA_SAMPLE_SIZE && item.getContent() != null) {
                        schemaSamples.add(item);
                    }
                } else {
                    failedCount++;
                }
            }
        }
    }
}
