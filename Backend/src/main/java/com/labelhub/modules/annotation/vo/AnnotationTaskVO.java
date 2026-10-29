package com.labelhub.modules.annotation.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 标注工作台任务信息。
 */
@Data
public class AnnotationTaskVO {

    private Long id;

    private String title;

    private String description;

    private LocalDateTime deadline;

    private Long templateId;

    private Long datasetId;
}
