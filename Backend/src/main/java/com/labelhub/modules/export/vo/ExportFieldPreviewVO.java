package com.labelhub.modules.export.vo;

import lombok.Data;

/**
 * 导出字段预览。
 */
@Data
public class ExportFieldPreviewVO {

    private String sourceName;

    private Object sampleValue;
}
