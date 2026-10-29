package com.labelhub.modules.template.handler.impl;

import com.labelhub.modules.template.dto.TemplateComponentDTO;
import com.labelhub.modules.template.handler.MaterialHandler;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 策略名称：展示项。
 * 处理方法：校验 source 必填，并将 source 写入展示项配置。
 */
@Component
public class ShowItemMaterialHandler extends AbstractMaterialHandler implements MaterialHandler {

    @Override
    public String supportType() {
        return "showItem";
    }

    @Override
    public void validate(TemplateComponentDTO component) {
        validateTextRequired(component.getField(), component.getSource(), " 的 source 不能为空");
    }

    @Override
    public Map<String, Object> buildConfig(TemplateComponentDTO component) {
        Map<String, Object> config = baseConfig(component);
        config.put("source", component.getSource());
        return config;
    }
}
