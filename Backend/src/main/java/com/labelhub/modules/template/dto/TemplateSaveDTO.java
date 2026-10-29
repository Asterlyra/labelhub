package com.labelhub.modules.template.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class TemplateSaveDTO {

    /** 模板名称 */
    @NotBlank(message = "模板名称不能为空")
    @Size(max = 200, message = "模板名称不能超过200个字符")
    private String name;

    /** 模板描述 */
    private String description;

    /** 物料组件列表 */
    @Valid
    @NotEmpty(message = "模板物料列表不能为空")
    private List<TemplateComponentDTO> components;
}
