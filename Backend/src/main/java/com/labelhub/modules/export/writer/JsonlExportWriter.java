package com.labelhub.modules.export.writer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelhub.common.exception.BusinessException;
import com.labelhub.modules.export.dto.ExportFieldMappingDTO;
import com.labelhub.modules.export.enums.ExportFormatEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class JsonlExportWriter implements ExportWriter {

    private final ObjectMapper objectMapper;

    @Override
    public ExportFormatEnum format() {
        return ExportFormatEnum.JSONL;
    }

    @Override
    public byte[] write(List<Map<String, Object>> rows, List<ExportFieldMappingDTO> fields) {
        StringBuilder builder = new StringBuilder();
        try {
            for (Map<String, Object> row : rows) {
                builder.append(objectMapper.writeValueAsString(row)).append('\n');
            }
            return builder.toString().getBytes(StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new BusinessException("JSONL 导出失败：" + e.getMessage());
        }
    }
}
