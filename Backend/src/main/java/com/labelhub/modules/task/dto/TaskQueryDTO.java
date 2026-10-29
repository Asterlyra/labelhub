package com.labelhub.modules.task.dto;

import lombok.Data;

/**
 * 任务分页查询参数。
 */
@Data
public class TaskQueryDTO {

    private String keyword;

    private Long datasetId;

    private Long templateId;

    private String status;

    private String tagName;

    private Integer pageNum = 1;

    private Integer pageSize = 10;
}
