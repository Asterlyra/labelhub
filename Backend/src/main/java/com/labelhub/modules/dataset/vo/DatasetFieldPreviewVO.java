package com.labelhub.modules.dataset.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 数据集字段预览响应。
 */
@Data
public class DatasetFieldPreviewVO {

    private String sourceFormat;

    private List<FieldPreview> fields = new ArrayList<>();

    /**
     * 单个源字段的预览信息。
     */
    @Data
    public static class FieldPreview {

        private String sourceName;

        private Object sampleValue;
    }
}
