package com.labelhub.modules.dataset.dto;

import lombok.Data;

/**
 * 数据项分页查询参数。
 */
@Data
public class DatasetItemQueryDTO {

    private String keyword;

    private String parseStatus;

    private String dataType;

    private Integer pageNum = 1;

    private Integer pageSize = 10;
}
