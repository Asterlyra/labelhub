package com.labelhub.modules.template.handler.impl;

import com.labelhub.modules.template.dto.TemplateComponentDTO;
import com.labelhub.modules.template.entity.Material;
import com.labelhub.modules.template.handler.MaterialHandler;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * 策略名称：标签选择。
 * 处理方法：校验标签选项非空、无空值且不重复，并将 options、nt 写入物料配置。
 */
@Component
public class TagSelectMaterialHandler extends AbstractMaterialHandler implements MaterialHandler {

    @Override
    public String supportType() {
        return "tagSelect";
    }

    @Override
    public void validate(TemplateComponentDTO component) {
        validateOptions(component);
    }

    @Override
    public Map<String, Object> buildConfig(TemplateComponentDTO component) {
        Map<String, Object> config = baseConfig(component);
        config.put("options", component.getOptions());
        config.put("multiple", isTrue(component.getMultiple()));
        return config;
    }

    @Override
    public void validateSubmission(Material material, Map<String, Object> config, Object value) {
        super.validateSubmission(material, config, value);
        if (isEmptySubmission(value)) {
            return;
        }
        Set<String> optionSet = readOptionSet(material, config);
        if (value instanceof Collection<?> collection) {
            for (Object item : collection) {
                String option = item == null ? "" : String.valueOf(item).trim();
                if (!optionSet.contains(option)) {
                    throw new com.labelhub.common.exception.BusinessException(material.getLabel() + " 的选项值不合法");
                }
            }
            return;
        }
        String option = String.valueOf(value).trim();
        if (!optionSet.contains(option)) {
            throw new com.labelhub.common.exception.BusinessException(material.getLabel() + " 的选项值不合法");
        }
    }
}
