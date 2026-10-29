package com.labelhub.modules.review.vo;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 审核工作台标注模板物料。
 */
@Data
public class ReviewMaterialVO {

    private Long id;

    private String type;

    private String field;

    private String label;

    private Boolean required;

    private Integer sortOrder;

    private Map<String, Object> config = new LinkedHashMap<>();
}
