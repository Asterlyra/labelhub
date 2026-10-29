package com.labelhub.modules.template.handler.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelhub.common.exception.BusinessException;
import com.labelhub.modules.template.dto.TemplateComponentDTO;
import com.labelhub.modules.template.handler.MaterialHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 策略名称：JSON编辑器。
 * 处理方法：校验 defaultValue 和 schema 字符串必须是合法 JSON，并保留扩展配置。
 */
@Component
@RequiredArgsConstructor
public class JsonEditorMaterialHandler extends AbstractMaterialHandler implements MaterialHandler {

    private final ObjectMapper objectMapper;

    @Override
    public String supportType() {
        return "jsonEditor";
    }

    @Override
    public void validate(TemplateComponentDTO component) {
        validateJsonText(component, "defaultValue");
        validateJsonText(component, "schema");
    }

    @Override
    public Map<String, Object> buildConfig(TemplateComponentDTO component) {
        return baseConfig(component);
    }

    @Override
    public void validateSubmission(com.labelhub.modules.template.entity.Material material, Map<String, Object> config, Object value) {
        super.validateSubmission(material, config, value);
        if (isEmptySubmission(value)) {
            return;
        }
        try {
            if (value instanceof String text) {
                objectMapper.readTree(text);
                return;
            }
            objectMapper.readTree(objectMapper.writeValueAsString(value));
        } catch (Exception e) {
            throw new BusinessException(material.getLabel() + " 不是合法的 JSON");
        }
    }

    private void validateJsonText(TemplateComponentDTO component, String key) {
        Object value = component.getExtraConfig().get(key);
        if (value instanceof String text && !text.trim().isEmpty()) {
            try {
                objectMapper.readTree(text);
            } catch (Exception e) {
                throw new BusinessException(component.getField() + " 的 " + key + " 不是合法的 JSON");
            }
        }
    }
}
