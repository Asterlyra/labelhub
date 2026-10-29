package com.labelhub.modules.template.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labelhub.modules.template.entity.Material;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface MaterialMapper extends BaseMapper<Material> {

    default int insertMaterial(Material material) {
        return insert(material);
    }

    default int deleteByTemplateId(Long templateId) {
        return delete(new LambdaQueryWrapper<Material>().eq(Material::getTemplateId, templateId));
    }

    default List<Material> selectByTemplateId(Long templateId) {
        return selectList(new LambdaQueryWrapper<Material>()
                .eq(Material::getTemplateId, templateId)
                .orderByAsc(Material::getSortOrder));
    }
}
