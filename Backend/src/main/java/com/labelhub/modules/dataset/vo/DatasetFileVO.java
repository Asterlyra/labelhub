package com.labelhub.modules.dataset.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据集文件响应对象。
 */
@Data
public class DatasetFileVO {

    private Long id;

    private Long datasetId;

    private String originalName;

    private String fileUrl;

    private String contentType;

    private Long fileSize;

    private String fileHash;

    private String format;

    private LocalDateTime createTime;
}
