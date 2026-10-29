package com.labelhub.modules.review.convert;

import com.labelhub.modules.review.vo.ReviewMaterialVO;
import com.labelhub.modules.review.vo.ReviewTaskBaseVO;
import com.labelhub.modules.task.entity.Task;
import com.labelhub.modules.template.entity.Material;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * 审核模块对象转换器。
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ReviewConvert {

    /**
     * 转换任务基础信息。
     */
    ReviewTaskBaseVO toTaskBaseVO(Task task);

    /**
     * 转换模板物料基础信息。
     */
    @Mapping(target = "required", expression = "java(material.getRequired() != null && material.getRequired() == 1)")
    @Mapping(target = "config", ignore = true)
    ReviewMaterialVO toMaterialVO(Material material);
}
