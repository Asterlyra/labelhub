package com.labelhub.modules.export.writer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelhub.common.exception.BusinessException;
import com.labelhub.modules.export.dto.ExportFieldMappingDTO;
import com.labelhub.modules.export.enums.ExportFormatEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class JsonExportWriter implements ExportWriter {

    private final ObjectMapper objectMapper;

    @Override
    public ExportFormatEnum format() {
        return ExportFormatEnum.JSON;
    }

    @Override
    public byte[] write(List<Map<String, Object>> rows, List<ExportFieldMappingDTO> fields) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(rows);
        } catch (JsonProcessingException e) {
            throw new BusinessException("JSON 导出失败：" + e.getMessage());
        }
    }
}
