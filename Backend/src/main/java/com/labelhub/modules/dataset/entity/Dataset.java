package com.labelhub.modules.dataset.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.labelhub.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 数据集主表实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dataset")
public class Dataset extends BaseEntity {

    private String name;

    private String description;

    private String sourceFormat;

    private String status;

    private Integer totalCount;

    private Integer successCount;

    private Integer failedCount;

    private String schemaJson;

    private Long createdBy;
}
