package com.labelhub.modules.export.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 导出任务视图对象。
 */
@Data
public class ExportJobVO {

    private Long id;

    private Long taskId;

    private String format;

    private String status;

    private String fileUrl;

    private String downloadUrl;

    private String errorMessage;

    private Map<String, Object> config;

    private Long createdBy;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
