package com.labelhub.modules.annotation.vo;

import lombok.Data;

import java.util.List;

/**
 * 标注工作台初始化响应。
 */
@Data
public class AnnotationInitVO {

    private AnnotationTaskVO task;

    private List<AnnotationMaterialVO> materials;

    private List<ItemNavigationVO> navigation;

    private AnnotationProgressVO progress;
}
