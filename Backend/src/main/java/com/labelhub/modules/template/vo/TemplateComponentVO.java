package com.labelhub.modules.template.vo;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class TemplateComponentVO {

    private Long id;

    /** 物料类型 */
    private String type;

    /** 字段标识 */
    private String field;

    /** 显示标签 */
    private String label;

    /** 是否必填 */
    private Boolean required;

    /** 排序序号 */
    private Integer sortOrder;

    /** 物料专属配置，通过 @JsonAnyGetter 展平到顶层 */
    @JsonIgnore
    private Map<String, Object> config = new LinkedHashMap<>();

    @JsonAnyGetter
    public Map<String, Object> getConfig() {
        return config;
    }
}
