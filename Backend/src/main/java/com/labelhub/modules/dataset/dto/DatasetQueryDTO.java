package com.labelhub.modules.dataset.dto;

import lombok.Data;

/**
 * 数据集分页查询参数。
 */
@Data
public class DatasetQueryDTO {

    private String keyword;

    private String status;

    private String sourceFormat;

    private Integer pageNum = 1;

    private Integer pageSize = 10;
}
