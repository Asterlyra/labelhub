package com.labelhub.modules.review.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

/**
 * 审核员直接修改标注数据请求体。
 */
@Data
public class ReviewModifyDTO {

    @NotNull(message = "标注数据不能为空")
    private Map<String, Object> annotationData;
}
