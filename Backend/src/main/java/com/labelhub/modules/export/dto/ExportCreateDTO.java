package com.labelhub.modules.export.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 创建导出任务请求。
 */
@Data
public class ExportCreateDTO {

    @NotBlank(message = "导出格式不能为空")
    private String format;

    @Valid
    @NotEmpty(message = "导出字段不能为空")
    private List<ExportFieldMappingDTO> fields;
}
