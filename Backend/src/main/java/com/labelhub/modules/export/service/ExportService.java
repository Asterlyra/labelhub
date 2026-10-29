package com.labelhub.modules.export.service;

import com.labelhub.modules.export.dto.ExportCreateDTO;
import com.labelhub.modules.export.vo.ExportFieldPreviewVO;
import com.labelhub.modules.export.vo.ExportJobVO;

import java.io.InputStream;
import java.util.List;

public interface ExportService {

    ExportJobVO createExport(Long taskId, ExportCreateDTO dto);

    List<ExportFieldPreviewVO> previewFields(Long taskId);

    List<ExportJobVO> listTaskExports(Long taskId);

    ExportJobVO getExport(Long exportId);

    ExportDownloadFile downloadExport(Long exportId);

    record ExportDownloadFile(InputStream inputStream, String filename, String contentType) {
    }
}
