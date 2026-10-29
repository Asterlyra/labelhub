package com.labelhub.modules.template.convert;

import com.labelhub.modules.template.dto.TemplateSaveDTO;
import com.labelhub.modules.template.entity.Material;
import com.labelhub.modules.template.entity.Template;
import com.labelhub.modules.template.vo.TemplateComponentVO;
import com.labelhub.modules.template.vo.TemplateVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface TemplateConvert {

    Template toEntity(TemplateSaveDTO dto);

    TemplateVO toVO(Template template);

    List<TemplateVO> toVOList(List<Template> templates);

    @Mapping(target = "required", expression = "java(material.getRequired() != null && material.getRequired() == 1)")
    @Mapping(target = "config", ignore = true)
    TemplateComponentVO toComponentVO(Material material);
}
