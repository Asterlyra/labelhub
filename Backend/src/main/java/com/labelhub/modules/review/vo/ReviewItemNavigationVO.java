package com.labelhub.modules.review.vo;

import lombok.Data;

/**
 * 审核项导航。
 */
@Data
public class ReviewItemNavigationVO {

    private Integer index;

    private Long taskItemId;

    private String status;

    private String displayStatus;

    private String displayStatusName;
}
