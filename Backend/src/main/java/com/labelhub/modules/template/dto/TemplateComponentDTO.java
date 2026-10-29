package com.labelhub.modules.template.dto;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class TemplateComponentDTO {

    /** 物料类型 */
    @NotBlank(message = "物料类型不能为空")
    private String type;

    /** 字段标识（英文，模板内唯一） */
    @NotBlank(message = "字段标识不能为空")
    private String field;

    /** 显示标签 */
    @NotBlank(message = "显示标签不能为空")
    private String label;

    /** 是否必填 */
    private Boolean required = false;

    /** 最大长度（input/textarea/richText） */
    private Integer maxLength;

    /** 占位提示文本 */
    private String placeholder;

    /** 是否多选（tagSelect） */
    private Boolean multiple;

    /** 选项列表（radio/checkbox/tagSelect） */
    private List<String> options;

    /** 数据源映射（showItem） */
    private String source;

    /** 模型编码（llmInteraction） */
    private String modelCode;

    /** 提示词模板（llmInteraction） */
    private String prompt;

    /** 输入字段列表（llmInteraction） */
    private List<String> inputFields;

    private final Map<String, Object> extraConfig = new LinkedHashMap<>();

    @JsonAnySetter
    public void putExtraConfig(String key, Object value) {
        extraConfig.put(key, value);
    }

    @JsonAnyGetter
    public Map<String, Object> getExtraConfig() {
        return extraConfig;
    }
}
