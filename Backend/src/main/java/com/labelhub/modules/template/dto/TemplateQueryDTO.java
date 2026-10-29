package com.labelhub.modules.template.dto;

import lombok.Data;

@Data
public class TemplateQueryDTO {

    /** 搜索关键词（模板名称） */
    private String keyword;

    /** 状态筛选：0禁用 1启用 */
    private Integer status;

    /** 页码 */
    private Integer pageNum = 1;

    /** 每页数量 */
    private Integer pageSize = 10;
}
