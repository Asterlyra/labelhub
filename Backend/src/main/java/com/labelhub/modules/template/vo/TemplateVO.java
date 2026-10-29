package com.labelhub.modules.template.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class TemplateVO {

    private Long id;

    /** 模板名称 */
    private String name;

    /** 模板描述 */
    private String description;

    /** 状态：0禁用 1启用 */
    private Integer status;

    /** 创建人ID */
    private Long creatorId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /** 物料组件列表 */
    private List<TemplateComponentVO> components;
}
