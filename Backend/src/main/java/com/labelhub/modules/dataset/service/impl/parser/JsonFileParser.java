package com.labelhub.modules.dataset.service.impl.parser;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelhub.modules.dataset.enums.ParseStatusEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * JSON 与 JSONL 文件解析器。
 */
@Component
@RequiredArgsConstructor
public class JsonFileParser implements FileParser {

    private final ObjectMapper objectMapper;

    @Override
    public ParseResult parse(InputStream inputStream) {
        return parseJson(inputStream);
    }

    /**
     * 解析标准 JSON 文件。
     */
    public ParseResult parseJson(InputStream inputStream) {
        try {
            JsonNode root = objectMapper.readTree(inputStream);
            ParseResult result = new ParseResult();
            if (root.isArray()) {
                int index = 1;
                for (JsonNode node : root) {
                    result.addItem(toSuccessItem(index, node));
                    index++;
                }
                return result;
            }

            JsonNode itemsNode = root.get("items");
            if (itemsNode != null && itemsNode.isArray()) {
                int index = 1;
                for (JsonNode node : itemsNode) {
                    result.addItem(toSuccessItem(index, node));
                    index++;
                }
                return result;
            }

            result.addItem(toSuccessItem(1, root));
            return result;
        } catch (Exception e) {
            throw new IllegalArgumentException("JSON 文件解析失败：" + e.getMessage(), e);
        }
    }

    /**
     * 按行解析 JSONL 文件。
     */
    public ParseResult parseJsonLines(InputStream inputStream) {
        ParseResult result = new ParseResult();
        parseJsonLinesBatches(inputStream, 1000, batch -> batch.forEach(result::addItem));
        return result;
    }

    /**
     * 按行分批解析 JSONL 文件。
     */
    public void parseJsonLinesBatches(InputStream inputStream, int batchSize, Consumer<List<ParsedItem>> batchConsumer) {
        List<ParsedItem> batch = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            int index = 1;
            while ((line = reader.readLine()) != null) {
                if (!StringUtils.hasText(line)) {
                    index++;
                    continue;
                }
                try {
                    JsonNode node = objectMapper.readTree(line);
                    batch.add(toSuccessItem(index, node));
                } catch (Exception e) {
                    batch.add(toFailedItem(index, e.getMessage()));
                }
                if (batch.size() >= batchSize) {
                    batchConsumer.accept(new ArrayList<>(batch));
                    batch.clear();
                }
                index++;
            }
            if (!batch.isEmpty()) {
                batchConsumer.accept(new ArrayList<>(batch));
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("JSONL 文件读取失败：" + e.getMessage(), e);
        }
    }

    private ParsedItem toSuccessItem(int index, JsonNode node) {
        Map<String, Object> content = node.isObject()
                ? objectMapper.convertValue(node, new TypeReference<LinkedHashMap<String, Object>>() {
        })
                : Map.of("value", objectMapper.convertValue(node, Object.class));

        ParsedItem item = new ParsedItem();
        item.setItemKey(readItemKey(content, index));
        item.setRowIndex(index);
        item.setContent(content);
        item.setDataType(DataTypeDetector.detect(content));
        item.setParseStatus(ParseStatusEnum.SUCCESS.getCode());
        return item;
    }

    private ParsedItem toFailedItem(int index, String message) {
        ParsedItem item = new ParsedItem();
        item.setItemKey(String.valueOf(index));
        item.setRowIndex(index);
        item.setContent(Map.of());
        item.setDataType(DataTypeDetector.detect(Map.of()));
        item.setParseStatus(ParseStatusEnum.FAILED.getCode());
        item.setErrorMessage(message);
        return item;
    }

    private String readItemKey(Map<String, Object> content, int index) {
        Object key = content.get("id");
        if (key == null) {
            key = content.get("key");
        }
        if (key == null) {
            key = content.get("itemKey");
        }
        return key == null ? String.valueOf(index) : String.valueOf(key);
    }
}
