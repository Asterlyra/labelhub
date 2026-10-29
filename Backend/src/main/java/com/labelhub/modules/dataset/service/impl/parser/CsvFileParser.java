package com.labelhub.modules.dataset.service.impl.parser;

import com.labelhub.modules.dataset.enums.ParseStatusEnum;
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
 * CSV 文件解析器，读取首行表头并转换为数据项。
 */
@Component
public class CsvFileParser implements FileParser {

    private static final char DELIMITER = ',';
    private static final char QUOTE = '"';
    private static final char UTF8_BOM = '\uFEFF';

    @Override
    public ParseResult parse(InputStream inputStream) {
        try {
            ParseResult result = new ParseResult();
            parseBatches(inputStream, 1000, batch -> batch.forEach(result::addItem));
            return result;
        } catch (Exception e) {
            throw new IllegalArgumentException("CSV 文件解析失败：" + e.getMessage(), e);
        }
    }

    @Override
    public void parseBatches(InputStream inputStream, int batchSize, Consumer<List<ParsedItem>> batchConsumer) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            List<String> currentFields = new ArrayList<>();
            StringBuilder currentField = new StringBuilder();
            boolean inQuotes = false;
            int recordStartLine = 1;
            CsvParseContext context = new CsvParseContext(batchSize, batchConsumer);

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (currentFields.isEmpty() && currentField.isEmpty()) {
                    recordStartLine = lineNumber;
                }

                inQuotes = parseLine(line, currentFields, currentField, inQuotes);
                if (inQuotes) {
                    currentField.append('\n');
                    continue;
                }

                currentFields.add(currentField.toString());
                acceptRecord(new CsvRecord(recordStartLine, List.copyOf(currentFields)), context);
                currentFields.clear();
                currentField.setLength(0);
            }

            if (inQuotes) {
                throw new IllegalArgumentException("存在未闭合的引号字段");
            }
            if (!currentFields.isEmpty() || !currentField.isEmpty()) {
                currentFields.add(currentField.toString());
                acceptRecord(new CsvRecord(recordStartLine, List.copyOf(currentFields)), context);
            }
            context.flush();
        } catch (Exception e) {
            throw new IllegalArgumentException("CSV 文件解析失败：" + e.getMessage(), e);
        }
    }

    private boolean parseLine(
            String line,
            List<String> fields,
            StringBuilder currentField,
            boolean inQuotes
    ) {
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == QUOTE) {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == QUOTE) {
                    currentField.append(QUOTE);
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
                continue;
            }
            if (ch == DELIMITER && !inQuotes) {
                fields.add(currentField.toString());
                currentField.setLength(0);
                continue;
            }
            currentField.append(ch);
        }
        return inQuotes;
    }

    private void acceptRecord(CsvRecord record, CsvParseContext context) {
        if (isBlankRecord(record.fields())) {
            return;
        }

        if (context.headers.isEmpty()) {
            context.headers.addAll(buildHeaders(record.fields()));
            return;
        }

        Map<String, Object> content = buildContent(context.headers, record.fields());
        if (!content.isEmpty()) {
            context.add(toItem(content, record.lineNumber()));
        }
    }

    private boolean isBlankRecord(List<String> fields) {
        return fields.stream().noneMatch(StringUtils::hasText);
    }

    private List<String> buildHeaders(List<String> fields) {
        List<String> headers = new ArrayList<>();
        for (int i = 0; i < fields.size(); i++) {
            String header = stripBom(fields.get(i));
            headers.add(StringUtils.hasText(header) ? header.trim() : "column" + (i + 1));
        }
        return headers;
    }

    private String stripBom(String value) {
        if (value != null && !value.isEmpty() && value.charAt(0) == UTF8_BOM) {
            return value.substring(1);
        }
        return value;
    }

    private Map<String, Object> buildContent(List<String> headers, List<String> fields) {
        Map<String, Object> content = new LinkedHashMap<>();
        int size = Math.min(headers.size(), fields.size());
        for (int i = 0; i < size; i++) {
            String value = fields.get(i);
            if (StringUtils.hasText(value)) {
                content.put(headers.get(i), value);
            }
        }
        return content;
    }

    private ParsedItem toItem(Map<String, Object> content, int rowNumber) {
        ParsedItem item = new ParsedItem();
        item.setItemKey(readItemKey(content, rowNumber));
        item.setRowIndex(rowNumber);
        item.setContent(content);
        item.setDataType(DataTypeDetector.detect(content));
        item.setParseStatus(ParseStatusEnum.SUCCESS.getCode());
        return item;
    }

    private String readItemKey(Map<String, Object> content, int rowNumber) {
        Object key = content.get("id");
        if (key == null) {
            key = content.get("key");
        }
        if (key == null) {
            key = content.get("itemKey");
        }
        return key == null ? String.valueOf(rowNumber) : String.valueOf(key);
    }

    private record CsvRecord(int lineNumber, List<String> fields) {
    }

    private static class CsvParseContext {

        private final int batchSize;
        private final Consumer<List<ParsedItem>> batchConsumer;
        private final List<String> headers = new ArrayList<>();
        private final List<ParsedItem> batch = new ArrayList<>();

        private CsvParseContext(int batchSize, Consumer<List<ParsedItem>> batchConsumer) {
            this.batchSize = batchSize;
            this.batchConsumer = batchConsumer;
        }

        private void add(ParsedItem item) {
            batch.add(item);
            if (batch.size() >= batchSize) {
                flush();
            }
        }

        private void flush() {
            if (batch.isEmpty()) {
                return;
            }
            batchConsumer.accept(new ArrayList<>(batch));
            batch.clear();
        }
    }
}
