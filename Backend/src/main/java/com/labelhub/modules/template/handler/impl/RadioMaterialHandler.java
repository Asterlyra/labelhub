package com.labelhub.modules.template.handler.impl;

import com.labelhub.modules.template.dto.TemplateComponentDTO;
import com.labelhub.modules.template.entity.Material;
import com.labelhub.modules.template.handler.MaterialHandler;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 策略名称：单选。
 * 处理方法：校验单选选项非空、无空值且不重复，并将 options 写入物料配置。
 */
@Component
public class RadioMaterialHandler extends AbstractMaterialHandler implements MaterialHandler {

    @Override
    public String supportType() {
        return "radio";
    }

    @Override
    public void validate(TemplateComponentDTO component) {
        validateOptions(component);
    }

    @Override
    public Map<String, Object> buildConfig(TemplateComponentDTO component) {
        Map<String, Object> config = baseConfig(component);
        config.put("options", component.getOptions());
        return config;
    }

    @Override
    public void validateSubmission(Material material, Map<String, Object> config, Object value) {
        super.validateSubmission(material, config, value);
        if (isEmptySubmission(value)) {
            return;
        }
        String option = String.valueOf(value).trim();
        if (!readOptionSet(material, config).contains(option)) {
            throw new com.labelhub.common.exception.BusinessException(material.getLabel() + " 的选项值不合法");
        }
    }
}
