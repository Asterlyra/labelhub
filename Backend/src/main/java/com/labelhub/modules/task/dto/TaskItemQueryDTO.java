package com.labelhub.modules.task.dto;

import lombok.Data;

/**
 * 任务数据项分页查询参数。
 */
@Data
public class TaskItemQueryDTO {

    private String status;

    private Integer pageNum = 1;

    private Integer pageSize = 10;
}
