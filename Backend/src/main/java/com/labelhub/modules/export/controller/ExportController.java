package com.labelhub.modules.export.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.labelhub.common.annotation.RateLimit;
import com.labelhub.common.result.R;
import com.labelhub.modules.export.dto.ExportCreateDTO;
import com.labelhub.modules.export.service.ExportService;
import com.labelhub.modules.export.service.ExportService.ExportDownloadFile;
import com.labelhub.modules.export.vo.ExportFieldPreviewVO;
import com.labelhub.modules.export.vo.ExportJobVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 任务结果导出接口。
 */
@RestController
@RequiredArgsConstructor
public class ExportController {

    private final ExportService exportService;

    @SaCheckRole("PROJECT_ADMIN")
    @GetMapping("/api/tasks/{taskId}/exports/preview-fields")
    public R<List<ExportFieldPreviewVO>> previewFields(@PathVariable Long taskId) {
        return R.ok(exportService.previewFields(taskId));
    }

    @SaCheckRole("PROJECT_ADMIN")
    @RateLimit(dimensions = RateLimit.Dimension.GLOBAL, count = 30, interval = 1, timeUnit = RateLimit.TimeUnit.MINUTES)
    @RateLimit(dimensions = RateLimit.Dimension.USER, count = 3, interval = 1, timeUnit = RateLimit.TimeUnit.MINUTES)
    @PostMapping("/api/tasks/{taskId}/exports")
    public R<ExportJobVO> create(@PathVariable Long taskId, @Valid @RequestBody ExportCreateDTO dto) {
        return R.ok(exportService.createExport(taskId, dto));
    }

    @SaCheckRole("PROJECT_ADMIN")
    @GetMapping("/api/tasks/{taskId}/exports")
    public R<List<ExportJobVO>> list(@PathVariable Long taskId) {
        return R.ok(exportService.listTaskExports(taskId));
    }

    @SaCheckRole("PROJECT_ADMIN")
    @GetMapping("/api/exports/{exportId}")
    public R<ExportJobVO> detail(@PathVariable Long exportId) {
        return R.ok(exportService.getExport(exportId));
    }

    @SaCheckRole("PROJECT_ADMIN")
    @GetMapping("/api/exports/{exportId}/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable Long exportId) {
        ExportDownloadFile file = exportService.downloadExport(exportId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.filename(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(new InputStreamResource(file.inputStream()));
    }
}
