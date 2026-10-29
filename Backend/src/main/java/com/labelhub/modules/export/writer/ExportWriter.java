package com.labelhub.modules.export.writer;

import com.labelhub.modules.export.dto.ExportFieldMappingDTO;
import com.labelhub.modules.export.enums.ExportFormatEnum;

import java.util.List;
import java.util.Map;

public interface ExportWriter {

    ExportFormatEnum format();

    byte[] write(List<Map<String, Object>> rows, List<ExportFieldMappingDTO> fields);
}
