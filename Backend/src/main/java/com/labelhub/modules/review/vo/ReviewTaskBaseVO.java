package com.labelhub.modules.review.vo;

import lombok.Data;

/**
 * 审核工作台任务基础信息。
 */
@Data
public class ReviewTaskBaseVO {

    private Long id;

    private String title;

    private String description;

    private Long templateId;

    private Long datasetId;
}
