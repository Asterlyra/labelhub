package com.labelhub.modules.export.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.labelhub.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 导出任务实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("export_job")
public class ExportJob extends BaseEntity {

    private Long taskId;

    private String format;

    private String status;

    private String configJson;

    private String fileUrl;

    private String errorMessage;

    private Long createdBy;
}
