package com.labelhub.modules.annotation.vo;

import lombok.Data;

/**
 * 当前标注员在任务内的工作台进度。
 */
@Data
public class AnnotationProgressVO {

    private Long total;

    private Long submitted;

    private Long draft;

    private Long pending;

    private Long rejected;

    private Long reviewed;
}
