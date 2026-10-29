package com.labelhub.modules.template.handler.impl;

import com.labelhub.modules.template.dto.TemplateComponentDTO;
import com.labelhub.modules.template.entity.Material;
import com.labelhub.modules.template.handler.MaterialHandler;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 策略名称：多行文本。
 * 处理方法：校验 maxLength 必须为正数，并将 maxLength、placeholder 写入物料配置。
 */
@Component
public class TextareaMaterialHandler extends AbstractMaterialHandler implements MaterialHandler {

    @Override
    public String supportType() {
        return "textarea";
    }

    @Override
    public void validate(TemplateComponentDTO component) {
        validatePositiveMaxLength(component);
    }

    @Override
    public Map<String, Object> buildConfig(TemplateComponentDTO component) {
        Map<String, Object> config = baseConfig(component);
        config.put("maxLength", component.getMaxLength());
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
