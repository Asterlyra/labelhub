package com.labelhub.modules.template.handler.impl;

import com.labelhub.modules.template.dto.TemplateComponentDTO;
import com.labelhub.modules.template.entity.Material;
import com.labelhub.modules.template.handler.MaterialHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * 策略名称：图片上传。
 * 处理方法：保留前端透传的扩展配置，不追加额外校验或配置字段。
 */
@Component
public class ImageUploadMaterialHandler extends AbstractMaterialHandler implements MaterialHandler {

    @Override
    public String supportType() {
        return "imageUpload";
    }

    @Override
    public void validate(TemplateComponentDTO component) {
    }

    @Override
    public Map<String, Object> buildConfig(TemplateComponentDTO component) {
        return baseConfig(component);
    }

    @Override
    public void validateSubmission(Material material, Map<String, Object> config, Object value) {
        super.validateSubmission(material, config, value);
        if (isEmptySubmission(value)) {
            return;
        }
        if (!(value instanceof String text) || !StringUtils.hasText(text)) {
            throw new com.labelhub.common.exception.BusinessException(material.getLabel() + " 必须是有效的图片地址");
        }
    }
}
