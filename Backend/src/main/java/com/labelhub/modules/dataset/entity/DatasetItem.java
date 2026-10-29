package com.labelhub.modules.dataset.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.labelhub.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 数据集解析后数据项实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dataset_item")
public class DatasetItem extends BaseEntity {

    private Long datasetId;

    private String itemKey;

    private Integer rowIndex;

    private String dataType;

    private String contentJson;

    private String parseStatus;

    private String errorMessage;
}
