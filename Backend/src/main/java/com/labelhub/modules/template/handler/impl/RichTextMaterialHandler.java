package com.labelhub.modules.template.handler.impl;

import com.labelhub.modules.template.dto.TemplateComponentDTO;
import com.labelhub.modules.template.entity.Material;
import com.labelhub.modules.template.handler.MaterialHandler;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 策略名称：富文本编辑器
 * 处理方法：当 maxLength 存在时校验其为正数，并将 maxLength、placeholder 写入物料配置。
 */
@Component
public class RichTextMaterialHandler extends AbstractMaterialHandler implements MaterialHandler {

    @Override
    public String supportType() {
        return "richText";
    }

    @Override
    public void validate(TemplateComponentDTO component) {
        if (component.getMaxLength() != null && component.getMaxLength() <= 0) {
            validatePositiveMaxLength(component);
        }
    }

    @Override
    public Map<String, Object> buildConfig(TemplateComponentDTO component) {
        Map<String, Object> config = baseConfig(component);
        putIfNotNull(config, "maxLength", component.getMaxLength());
        putIfNotNull(config, "placeholder", component.getPlaceholder());
        return config;
    }

    @Override
    public void validateSubmission(Material material, Map<String, Object> config, Object value) {
        super.validateSubmission(material, config, value);
        if (isEmptySubmission(value)) {
            return;
        }
        String text = String.valueOf(value);
        Integer maxLength = readIntegerConfig(material, config, "maxLength");
        if (maxLength != null && text.length() > maxLength) {
            throw new com.labelhub.common.exception.BusinessException(material.getLabel() + " 超出最大长度限制");
        }
    }
}
