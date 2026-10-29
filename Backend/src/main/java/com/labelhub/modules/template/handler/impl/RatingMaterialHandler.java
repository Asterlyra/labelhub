package com.labelhub.modules.template.handler.impl;

import com.labelhub.common.exception.BusinessException;
import com.labelhub.modules.template.dto.TemplateComponentDTO;
import com.labelhub.modules.template.entity.Material;
import com.labelhub.modules.template.handler.MaterialHandler;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 策略名称：评分。
 * 处理方法：固定支持 1-5 星评分，并将 maxScore 写入物料配置。
 */
@Component
public class RatingMaterialHandler extends AbstractMaterialHandler implements MaterialHandler {

    private static final int DEFAULT_MAX_SCORE = 5;

    @Override
    public String supportType() {
        return "rating";
    }

    @Override
    public void validate(TemplateComponentDTO component) {
        Object maxScore = component.getExtraConfig().get("maxScore");
        if (maxScore == null) {
            return;
        }
        int value = parseMaxScore(component, maxScore);
        if (value != DEFAULT_MAX_SCORE) {
            throw new BusinessException(component.getField() + " 的 maxScore 仅支持 5");
        }
    }

    @Override
    public Map<String, Object> buildConfig(TemplateComponentDTO component) {
        Map<String, Object> config = baseConfig(component);
        config.put("maxScore", DEFAULT_MAX_SCORE);
        return config;
    }

    @Override
    public void validateSubmission(Material material, Map<String, Object> config, Object value) {
        super.validateSubmission(material, config, value);
        if (isEmptySubmission(value)) {
            return;
        }
        double score;
        try {
            score = Double.parseDouble(String.valueOf(value).trim());
        } catch (Exception e) {
            throw new BusinessException(material.getLabel() + " 必须是 1 到 5 的评分");
        }
        Integer maxScore = readIntegerConfig(material, config, "maxScore");
        int effectiveMaxScore = maxScore == null ? DEFAULT_MAX_SCORE : maxScore;
        if (score < 1 || score > effectiveMaxScore || score != Math.floor(score)) {
            throw new BusinessException(material.getLabel() + " 必须是 1 到 5 的整数评分");
        }
    }

    /**
     * 解析评分最大值。
     */
    private int parseMaxScore(TemplateComponentDTO component, Object maxScore) {
        try {
            return Integer.parseInt(String.valueOf(maxScore));
        } catch (Exception e) {
            throw new BusinessException(component.getField() + " 的 maxScore 必须是数字");
        }
    }
}
