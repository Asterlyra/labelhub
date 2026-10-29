package com.labelhub.modules.template.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labelhub.modules.template.dto.TemplateQueryDTO;
import com.labelhub.modules.template.entity.Template;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.util.StringUtils;

@Mapper
public interface TemplateMapper extends BaseMapper<Template> {

    default int insertTemplate(Template template) {
        return insert(template);
    }

    default Template selectTemplateById(Long id) {
        return selectById(id);
    }

    default int updateTemplateById(Template template) {
        return updateById(template);
    }

    default int deleteTemplateById(Long id) {
        return deleteById(id);
    }

    default Page<Template> selectTemplatePage(TemplateQueryDTO dto) {
        Page<Template> page = new Page<>(dto.getPageNum(), dto.getPageSize());
        LambdaQueryWrapper<Template> wrapper = new LambdaQueryWrapper<Template>()
                .like(StringUtils.hasText(dto.getKeyword()), Template::getName, dto.getKeyword())
                .eq(dto.getStatus() != null, Template::getStatus, dto.getStatus())
                .orderByDesc(Template::getCreateTime);
        return selectPage(page, wrapper);
    }
}
