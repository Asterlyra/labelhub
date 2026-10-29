package com.labelhub.modules.template.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelhub.common.annotation.RateLimit;
import com.labelhub.common.exception.BusinessException;
import com.labelhub.common.result.R;
import com.labelhub.modules.template.dto.TemplateQueryDTO;
import com.labelhub.modules.template.dto.TemplateSaveDTO;
import com.labelhub.modules.template.service.TemplateService;
import com.labelhub.modules.template.vo.TemplateVO;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/templates")
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateService templateService;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    @SaCheckRole("PROJECT_ADMIN")
    @RateLimit(dimensions = RateLimit.Dimension.USER, count = 20, interval = 1, timeUnit = RateLimit.TimeUnit.MINUTES)
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public R<TemplateVO> create(@Valid @RequestBody TemplateSaveDTO dto) {
        return R.ok(templateService.createTemplate(dto));
    }

    @SaCheckRole("PROJECT_ADMIN")
    @RateLimit(dimensions = RateLimit.Dimension.USER, count = 20, interval = 1, timeUnit = RateLimit.TimeUnit.MINUTES)
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<TemplateVO> createWithFiles(@RequestParam("template") String template,
                                         @RequestPart(value = "files", required = false) List<MultipartFile> files,
                                         @RequestParam(value = "fileFields", required = false) List<String> fileFields) {
        TemplateSaveDTO dto = parseTemplate(template);
        return R.ok(templateService.createTemplate(dto, buildFileMap(files, fileFields)));
    }

    @SaCheckRole("PROJECT_ADMIN")
    @GetMapping
    public R<Page<TemplateVO>> list(TemplateQueryDTO dto) {
        return R.ok(templateService.listTemplates(dto));
    }

    /**
     * 分页查询当前登录用户创建的模板。
     */
    @SaCheckRole("PROJECT_ADMIN")
    @GetMapping("/my")
    public R<Page<TemplateVO>> listMine(TemplateQueryDTO dto) {
        return R.ok(templateService.listMyTemplates(dto));
    }

    @SaCheckRole("PROJECT_ADMIN")
    @GetMapping("/{id}")
    public R<TemplateVO> detail(@PathVariable Long id) {
        return R.ok(templateService.getTemplateDetail(id));
    }

    @SaCheckRole("PROJECT_ADMIN")
    @RateLimit(dimensions = RateLimit.Dimension.USER, count = 20, interval = 1, timeUnit = RateLimit.TimeUnit.MINUTES)
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody TemplateSaveDTO dto) {
        templateService.updateTemplate(id, dto);
        return R.ok();
    }

    @SaCheckRole("PROJECT_ADMIN")
    @RateLimit(dimensions = RateLimit.Dimension.USER, count = 20, interval = 1, timeUnit = RateLimit.TimeUnit.MINUTES)
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<Void> updateWithFiles(@PathVariable Long id,
                                   @RequestParam("template") String template,
                                   @RequestPart(value = "files", required = false) List<MultipartFile> files,
                                   @RequestParam(value = "fileFields", required = false) List<String> fileFields) {
        TemplateSaveDTO dto = parseTemplate(template);
        templateService.updateTemplate(id, dto, buildFileMap(files, fileFields));
        return R.ok();
    }

    @SaCheckRole("PROJECT_ADMIN")
    @RateLimit(dimensions = RateLimit.Dimension.USER, count = 20, interval = 1, timeUnit = RateLimit.TimeUnit.MINUTES)
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        templateService.deleteTemplate(id);
        return R.ok();
    }

    private TemplateSaveDTO parseTemplate(String template) {
        try {
            TemplateSaveDTO dto = objectMapper.readValue(template, TemplateSaveDTO.class);
            validate(dto);
            return dto;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("模板 JSON 解析失败：" + e.getMessage());
        }
    }

    private void validate(TemplateSaveDTO dto) {
        Set<ConstraintViolation<TemplateSaveDTO>> violations = validator.validate(dto);
        if (!violations.isEmpty()) {
            throw new BusinessException(violations.iterator().next().getMessage());
        }
    }

    private Map<String, MultipartFile> buildFileMap(List<MultipartFile> files, List<String> fileFields) {
        if ((files == null || files.isEmpty()) && (fileFields == null || fileFields.isEmpty())) {
            return Map.of();
        }
        if (files == null || fileFields == null || files.size() != fileFields.size()) {
            throw new BusinessException("上传文件数量与 fileFields 数量不一致");
        }

        Map<String, MultipartFile> fileMap = new HashMap<>();
        for (int i = 0; i < files.size(); i++) {
            String field = fileFields.get(i);
            if (field == null || field.trim().isEmpty()) {
                throw new BusinessException("fileFields 不能为空");
            }
            fileMap.put(field.trim(), files.get(i));
        }
        return fileMap;
    }
}
