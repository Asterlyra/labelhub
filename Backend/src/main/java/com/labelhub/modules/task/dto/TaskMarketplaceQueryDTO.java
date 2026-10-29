package com.labelhub.modules.task.dto;

import lombok.Data;

import java.util.List;

/**
 * 任务广场查询参数。
 */
@Data
public class TaskMarketplaceQueryDTO {

    private String keyword;

    private List<String> tags;

    private Integer pageNum = 1;

    private Integer pageSize = 10;
}
