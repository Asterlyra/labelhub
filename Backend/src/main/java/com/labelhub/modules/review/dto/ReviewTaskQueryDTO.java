package com.labelhub.modules.review.dto;

import lombok.Data;

/**
 * 我的审核任务分页查询参数。
 */
@Data
public class ReviewTaskQueryDTO {

    private String keyword;

    private Integer pageNum = 1;

    private Integer pageSize = 10;
}
