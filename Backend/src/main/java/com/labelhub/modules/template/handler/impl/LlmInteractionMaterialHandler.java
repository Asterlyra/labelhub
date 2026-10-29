package com.labelhub.modules.template.handler.impl;

import com.labelhub.modules.template.dto.TemplateComponentDTO;
import com.labelhub.modules.template.handler.MaterialHandler;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 策略名称：AI辅助标注开关。
 * 处理方法：仅作为模板是否启用 AI 辅助标注的标记，不需要额外配置。
 */
@Component
public class LlmInteractionMaterialHandler extends AbstractMaterialHandler implements MaterialHandler {

    @Override
    public String supportType() {
        return "llmInteraction";
    }

    @Override
    public void validate(TemplateComponentDTO component) {
    }

    @Override
    public Map<String, Object> buildConfig(TemplateComponentDTO component) {
        return baseConfig(component);
    }
}
