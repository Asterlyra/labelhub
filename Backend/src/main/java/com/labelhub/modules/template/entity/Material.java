package com.labelhub.modules.template.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("material")
public class Material {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属模板ID */
    private Long templateId;

    /** 物料类型：input/textarea/radio/checkbox/tagSelect/rating/richText/fileUpload/imageUpload/jsonEditor/llmInteraction/showItem */
    private String type;

    /** 字段标识（英文，模板内唯一） */
    private String field;

    /** 显示标签 */
    private String label;

    /** 是否必填：0否 1是 */
    private Integer required;

    /** 排序序号 */
    private Integer sortOrder;

    /** 物料专属配置（JSON格式） */
    private String config;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
