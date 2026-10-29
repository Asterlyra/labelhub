package com.labelhub.modules.export.writer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelhub.modules.export.dto.ExportFieldMappingDTO;
import com.labelhub.modules.export.enums.ExportFormatEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class CsvExportWriter implements ExportWriter {

    private final ObjectMapper objectMapper;

    @Override
    public ExportFormatEnum format() {
        return ExportFormatEnum.CSV;
    }

    @Override
    public byte[] write(List<Map<String, Object>> rows, List<ExportFieldMappingDTO> fields) {
        StringBuilder builder = new StringBuilder("\uFEFF");
        appendLine(builder, fields.stream().map(ExportFieldMappingDTO::getTargetName).toList());
        for (Map<String, Object> row : rows) {
            appendLine(builder, fields.stream()
                    .map(field -> valueToText(row.get(field.getTargetName())))
                    .toList());
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void appendLine(StringBuilder builder, List<String> values) {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(escape(values.get(i)));
        }
        builder.append("\r\n");
    }

    private String escape(String value) {
        String text = value == null ? "" : value;
        boolean needQuote = text.contains(",") || text.contains("\"") || text.contains("\r") || text.contains("\n");
        String escaped = text.replace("\"", "\"\"");
        return needQuote ? "\"" + escaped + "\"" : escaped;
    }

    private String valueToText(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof CharSequence || value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }
}
