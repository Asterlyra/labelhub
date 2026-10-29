package com.labelhub.modules.review.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 审核驳回请求体。
 */
@Data
public class ReviewRejectDTO {

    @NotBlank(message = "审核意见不能为空")
    private String reviewComment;
}
