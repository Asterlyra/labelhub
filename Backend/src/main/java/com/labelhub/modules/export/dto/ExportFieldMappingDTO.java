package com.labelhub.modules.export.dto;

import lombok.Data;

/**
 * 导出字段映射配置。
 */
@Data
public class ExportFieldMappingDTO {

    private String sourceName;

    private String targetName;

    private Boolean enabled;
}
