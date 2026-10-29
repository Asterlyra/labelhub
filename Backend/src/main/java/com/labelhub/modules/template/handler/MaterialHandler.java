package com.labelhub.modules.template.handler;

import com.labelhub.modules.template.dto.TemplateComponentDTO;
import com.labelhub.modules.template.entity.Material;

import java.util.Map;

public interface MaterialHandler {

    /** 返回该处理器负责的物料类型标识 */
    String supportType();

    /** 校验物料配置是否合法 */
    void validate(TemplateComponentDTO component);

    /** 将物料配置构建为持久化所需的Map结构 */
    Map<String, Object> buildConfig(TemplateComponentDTO component);

    /** 校验提交值是否合法 */
    default void validateSubmission(Material material, Map<String, Object> config, Object value) {
    }
}
