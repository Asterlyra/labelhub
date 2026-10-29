package com.labelhub.modules.annotation.vo;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 标注表单物料。
 */
@Data
public class AnnotationMaterialVO {

    private Long id;

    private String type;

    private String field;

    private String label;

    private Boolean required;

    private Integer sortOrder;

    private Map<String, Object> config = new LinkedHashMap<>();
}
