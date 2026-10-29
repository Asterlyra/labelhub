package com.labelhub.modules.review.vo;

import lombok.Data;

import java.util.List;

/**
 * 审核工作台初始化响应。
 */
@Data
public class ReviewWorkspaceInitVO {

    private ReviewTaskBaseVO task;

    private List<ReviewMaterialVO> materials;

    private List<ReviewItemNavigationVO> navigation;

    private ReviewProgressVO progress;
}
