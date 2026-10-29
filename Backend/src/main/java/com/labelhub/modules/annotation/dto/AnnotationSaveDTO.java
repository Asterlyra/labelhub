package com.labelhub.modules.annotation.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

/**
 * 保存草稿/提交标注请求体。
 */
@Data
public class AnnotationSaveDTO {

    @NotNull(message = "标注数据不能为空")
    private Map<String, Object> annotationData;
}
