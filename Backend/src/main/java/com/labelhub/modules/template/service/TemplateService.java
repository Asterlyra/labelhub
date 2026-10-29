package com.labelhub.modules.template.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labelhub.modules.template.dto.TemplateQueryDTO;
import com.labelhub.modules.template.dto.TemplateSaveDTO;
import com.labelhub.modules.template.vo.TemplateVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface TemplateService {

    TemplateVO createTemplate(TemplateSaveDTO dto);

    TemplateVO createTemplate(TemplateSaveDTO dto, Map<String, MultipartFile> componentFiles);

    Page<TemplateVO> listTemplates(TemplateQueryDTO dto);

    /**
     * 查询当前登录用户创建的模板分页列表。
     */
    Page<TemplateVO> listMyTemplates(TemplateQueryDTO dto);

    TemplateVO getTemplateDetail(Long id);

    void updateTemplate(Long id, TemplateSaveDTO dto);

    void updateTemplate(Long id, TemplateSaveDTO dto, Map<String, MultipartFile> componentFiles);

    void deleteTemplate(Long id);
}
