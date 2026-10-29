package com.labelhub.modules.dataset.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 数据集原始文件实体。
 */
@Data
@TableName("dataset_file")
public class DatasetFile implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long datasetId;

    private String originalName;

    private String fileUrl;

    private String contentType;

    private Long fileSize;

    private String fileHash;

    private String format;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
