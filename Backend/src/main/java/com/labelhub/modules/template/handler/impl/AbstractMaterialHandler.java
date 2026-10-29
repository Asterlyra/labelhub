package com.labelhub.modules.template.handler.impl;

import com.labelhub.common.exception.BusinessException;
import com.labelhub.modules.template.dto.TemplateComponentDTO;
import com.labelhub.modules.template.entity.Material;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 策略基类：提供物料策略通用处理方法，包括基础配置合并、长度校验、选项校验和空文本校验。
 */
public abstract class AbstractMaterialHandler implements com.labelhub.modules.template.handler.MaterialHandler {

    /** 合并前端透传的扩展配置，作为物料配置的基础 */
    protected Map<String, Object> baseConfig(TemplateComponentDTO component) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.putAll(component.getExtraConfig());
        return config;
    }

    /** 校验 maxLength 必须大于 0 */
    protected void validatePositiveMaxLength(TemplateComponentDTO component) {
        if (component.getMaxLength() == null || component.getMaxLength() <= 0) {
            throw new BusinessException(component.getField() + " 的 maxLength 必须大于 0");
        }
    }

    /** 当 value 不为 null 时写入配置 */
    protected void putIfNotNull(Map<String, Object> config, String key, Object value) {
        if (value != null) {
            config.put(key, value);
        }
    }

    /** 校验选项列表：非空、不含空值、不重复 */
    protected void validateOptions(TemplateComponentDTO component) {
        List<String> options = component.getOptions();
        if (options == null || options.isEmpty()) {
            throw new BusinessException(component.getField() + " 的选项列表不能为空");
        }

        List<String> normalized = new ArrayList<>();
        for (String option : options) {
            if (option == null || option.trim().isEmpty()) {
                throw new BusinessException(component.getField() + " 的选项列表不能包含空值");
            }
            String value = option.trim();
            if (normalized.contains(value)) {
                throw new BusinessException(component.getField() + " 的选项列表不能有重复项");
            }
            normalized.add(value);
        }
    }

    /** 校验文本字段必填 */
    protected void validateTextRequired(String fieldName, String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new BusinessException(fieldName + message);
        }
    }

    protected boolean isTrue(Boolean value) {
        return Objects.equals(Boolean.TRUE, value);
    }

    /** 判断提交值是否为空 */
    protected boolean isEmptySubmission(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String text) {
            return text.trim().isEmpty();
        }
        if (value instanceof Collection<?> collection) {
            return collection.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return map.isEmpty();
        }
        return false;
    }

    @Override
    public void validateSubmission(Material material, Map<String, Object> config, Object value) {
        if (material.getRequired() != null && material.getRequired() == 1 && isEmptySubmission(value)) {
            throw new BusinessException("必填项不能为空：" + material.getLabel());
        }
    }

    /** 解析整型配置 */
    protected Integer readIntegerConfig(Material material, Map<String, Object> config, String key) {
        if (config == null || !config.containsKey(key) || config.get(key) == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(config.get(key)).trim());
        } catch (Exception e) {
            throw new BusinessException(material.getLabel() + " 的 " + key + " 配置不合法");
        }
    }

    /** 从配置中读取选项列表 */
    @SuppressWarnings("unchecked")
    protected Set<String> readOptionSet(Material material, Map<String, Object> config) {
        Object rawOptions = config == null ? null : config.get("options");
        if (!(rawOptions instanceof List<?> options)) {
            throw new BusinessException(material.getLabel() + " 的选项配置不合法");
        }
        Set<String> optionSet = new HashSet<>();
        for (Object option : options) {
            if (option == null || String.valueOf(option).trim().isEmpty()) {
                throw new BusinessException(material.getLabel() + " 的选项配置不合法");
            }
            optionSet.add(String.valueOf(option).trim());
        }
        return optionSet;
    }
}
