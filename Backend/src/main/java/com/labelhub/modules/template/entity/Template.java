package com.labelhub.modules.template.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.labelhub.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("template")
public class Template extends BaseEntity {

    /** 模板名称 */
    private String name;

    /** 模板描述 */
    private String description;

    /** 状态：0禁用 1启用 */
    private Integer status;

    /** 创建人ID */
    private Long creatorId;
}
