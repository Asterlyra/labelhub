package com.labelhub.modules.template.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelhub.auth.util.AuthUtil;
import com.labelhub.common.constant.MinioBucketConstant;
import com.labelhub.common.constant.MinioObjectDirectoryConstant;
import com.labelhub.common.constant.RedisKeyConstant;
import com.labelhub.common.exception.BusinessException;
import com.labelhub.common.exception.ErrorCode;
import com.labelhub.common.util.MinIOUtil;
import com.labelhub.common.util.RedisUtil;
import com.labelhub.modules.task.entity.Task;
import com.labelhub.modules.task.enums.TaskStatusEnum;
import com.labelhub.modules.task.mapper.TaskMapper;
import com.labelhub.modules.template.convert.TemplateConvert;
import com.labelhub.modules.template.dto.TemplateComponentDTO;
import com.labelhub.modules.template.dto.TemplateQueryDTO;
import com.labelhub.modules.template.dto.TemplateSaveDTO;
import com.labelhub.modules.template.entity.Material;
import com.labelhub.modules.template.entity.Template;
import com.labelhub.modules.template.handler.MaterialHandler;
import com.labelhub.modules.template.handler.MaterialHandlerFactory;
import com.labelhub.modules.template.mapper.MaterialMapper;
import com.labelhub.modules.template.mapper.TemplateMapper;
import com.labelhub.modules.template.service.TemplateService;
import com.labelhub.modules.template.vo.TemplateComponentVO;
import com.labelhub.modules.template.vo.TemplateVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateServiceImpl extends ServiceImpl<TemplateMapper, Template> implements TemplateService {

    private static final long DEFAULT_PAGE_NUM = 1L;
    private static final long DEFAULT_PAGE_SIZE = 10L;
    private static final String IMAGE_UPLOAD_TYPE = "imageUpload";
    private static final String FILE_UPLOAD_TYPE = "fileUpload";

    private final MaterialMapper materialMapper;
    private final MaterialHandlerFactory materialHandlerFactory;
    private final ObjectMapper objectMapper;
    private final TemplateConvert templateConvert;
    private final MinIOUtil minIOUtil;
    private final TaskMapper taskMapper;
    private final RedisUtil redisUtil;

    @Override
    @Transactional
    public TemplateVO createTemplate(TemplateSaveDTO dto) {
        return createTemplate(dto, Map.of());
    }

    @Override
    @Transactional
    public TemplateVO createTemplate(TemplateSaveDTO dto, Map<String, MultipartFile> componentFiles) {
        log.info("创建模板开始：name={}", dto.getName());
        fillUploadConfig(dto.getComponents(), componentFiles);
        validateComponents(dto.getComponents());

        Template template = templateConvert.toEntity(dto);
        template.setStatus(1);
        template.setCreatorId(AuthUtil.getCurrentUserId());
        save(template);

        saveMaterials(template.getId(), dto.getComponents());
        clearTemplateRuntimeCache(template.getId());
        log.info("创建模板成功：templateId={}", template.getId());
        return getTemplateDetail(template.getId());
    }

    @Override
    public Page<TemplateVO> listTemplates(TemplateQueryDTO dto) {
        TemplateQueryDTO query = dto == null ? new TemplateQueryDTO() : dto;
        long pageNum = positiveOrDefault(query.getPageNum(), DEFAULT_PAGE_NUM);
        long pageSize = positiveOrDefault(query.getPageSize(), DEFAULT_PAGE_SIZE);

        log.info("查询模板列表开始：keyword={}，status={}，pageNum={}，pageSize={}",
                query.getKeyword(), query.getStatus(), pageNum, pageSize);
        Page<Template> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Template> wrapper = new LambdaQueryWrapper<Template>()
                .like(StringUtils.hasText(query.getKeyword()), Template::getName, query.getKeyword())
                .eq(query.getStatus() != null, Template::getStatus, query.getStatus())
                .orderByDesc(Template::getCreateTime);

        Page<Template> result = page(page, wrapper);
        Page<TemplateVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(templateConvert.toVOList(result.getRecords()));
        log.info("查询模板列表成功：total={}", result.getTotal());
        return voPage;
    }

    @Override
    public Page<TemplateVO> listMyTemplates(TemplateQueryDTO dto) {
        TemplateQueryDTO query = dto == null ? new TemplateQueryDTO() : dto;
        long pageNum = positiveOrDefault(query.getPageNum(), DEFAULT_PAGE_NUM);
        long pageSize = positiveOrDefault(query.getPageSize(), DEFAULT_PAGE_SIZE);
        Long currentUserId = AuthUtil.getCurrentUserId();

        log.info("查询我的模板列表开始：userId={}，keyword={}，status={}，pageNum={}，pageSize={}",
                currentUserId, query.getKeyword(), query.getStatus(), pageNum, pageSize);
        try {
            Page<Template> page = new Page<>(pageNum, pageSize);
            LambdaQueryWrapper<Template> wrapper = new LambdaQueryWrapper<Template>()
                    .eq(Template::getCreatorId, currentUserId)
                    .like(StringUtils.hasText(query.getKeyword()), Template::getName, query.getKeyword())
                    .eq(query.getStatus() != null, Template::getStatus, query.getStatus())
                    .orderByDesc(Template::getCreateTime);

            Page<Template> result = page(page, wrapper);
            Page<TemplateVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
            voPage.setRecords(templateConvert.toVOList(result.getRecords()));
            log.info("查询我的模板列表成功：userId={}，total={}", currentUserId, result.getTotal());
            return voPage;
        } catch (Exception e) {
            log.error("查询我的模板列表失败：userId={}，原因={}", currentUserId, e.getMessage(), e);
            throw new BusinessException("查询我的模板失败：" + e.getMessage());
        }
    }

    @Override
    public TemplateVO getTemplateDetail(Long id) {
        log.info("查询模板详情开始：templateId={}", id);
        Template template = getById(id);
        if (template == null) {
            throw new BusinessException(ErrorCode.TEMPLATE_NOT_FOUND);
        }

        TemplateVO vo = templateConvert.toVO(template);
        vo.setComponents(listComponentVOs(id));
        log.info("查询模板详情成功：templateId={}", id);
        return vo;
    }

    @Override
    @Transactional
    public void updateTemplate(Long id, TemplateSaveDTO dto) {
        updateTemplate(id, dto, Map.of());
    }

    @Override
    @Transactional
    public void updateTemplate(Long id, TemplateSaveDTO dto, Map<String, MultipartFile> componentFiles) {
        log.info("更新模板开始：templateId={}", id);
        Template template = getById(id);
        if (template == null) {
            throw new BusinessException(ErrorCode.TEMPLATE_NOT_FOUND);
        }
        assertTemplateCanChange(id, "更新");

        fillUploadConfig(dto.getComponents(), componentFiles);
        validateComponents(dto.getComponents());

        Template update = templateConvert.toEntity(dto);
        update.setId(id);
        updateById(update);

        materialMapper.delete(new LambdaQueryWrapper<Material>().eq(Material::getTemplateId, id));
        saveMaterials(id, dto.getComponents());
        clearTemplateRuntimeCache(id);
        log.info("更新模板成功：templateId={}", id);
    }

    @Override
    @Transactional
    public void deleteTemplate(Long id) {
        log.info("删除模板开始：templateId={}", id);
        Template template = getById(id);
        if (template == null) {
            throw new BusinessException(ErrorCode.TEMPLATE_NOT_FOUND);
        }
        assertTemplateCanChange(id, "删除");
        removeById(id);
        materialMapper.delete(new LambdaQueryWrapper<Material>().eq(Material::getTemplateId, id));
        clearTemplateRuntimeCache(id);
        log.info("删除模板成功：templateId={}", id);
    }

    private void assertTemplateCanChange(Long templateId, String action) {
        Long activeTaskCount = taskMapper.selectCount(new LambdaQueryWrapper<Task>()
                .eq(Task::getTemplateId, templateId)
                .ne(Task::getStatus, TaskStatusEnum.ENDED.getCode()));
        if (activeTaskCount != null && activeTaskCount > 0) {
            throw new BusinessException("模板已绑定未结束任务，不能" + action);
        }
    }

    private void fillUploadConfig(List<TemplateComponentDTO> components, Map<String, MultipartFile> componentFiles) {
        if (componentFiles == null || componentFiles.isEmpty()) {
            return;
        }

        Set<String> matchedFields = new HashSet<>();
        for (TemplateComponentDTO component : components) {
            MultipartFile file = componentFiles.get(component.getField());
            if (file == null) {
                continue;
            }
            if (!isUploadComponent(component)) {
                throw new BusinessException("字段不是图片或文件上传物料，不能绑定文件：" + component.getField());
            }

            matchedFields.add(component.getField());
            MinIOUtil.UploadResult uploadResult = uploadMaterialFile(component, file);
            putUploadConfig(component, uploadResult);
        }

        Set<String> unknownFields = new HashSet<>(componentFiles.keySet());
        unknownFields.removeAll(matchedFields);
        if (!unknownFields.isEmpty()) {
            throw new BusinessException("上传文件未匹配到图片或文件物料字段：" + unknownFields);
        }
    }

    private MinIOUtil.UploadResult uploadMaterialFile(TemplateComponentDTO component, MultipartFile file) {
        if (IMAGE_UPLOAD_TYPE.equals(component.getType())) {
            validateImageFile(component, file);
            return minIOUtil.upload(file, MinioBucketConstant.TEMPLATE, MinioObjectDirectoryConstant.IMAGES);
        }
        return minIOUtil.upload(file, MinioBucketConstant.TEMPLATE, MinioObjectDirectoryConstant.FILES);
    }

    private void validateImageFile(TemplateComponentDTO component, MultipartFile file) {
        String contentType = file.getContentType();
        if (!StringUtils.hasText(contentType) || !contentType.startsWith("image/")) {
            throw new BusinessException(component.getField() + " 只能上传图片文件");
        }
    }

    private void putUploadConfig(TemplateComponentDTO component, MinIOUtil.UploadResult uploadResult) {
        Map<String, Object> config = component.getExtraConfig();
        config.clear();
        config.put("url", uploadResult.url());
    }

    private boolean isUploadComponent(TemplateComponentDTO component) {
        return IMAGE_UPLOAD_TYPE.equals(component.getType()) || FILE_UPLOAD_TYPE.equals(component.getType());
    }

    private void validateComponents(List<TemplateComponentDTO> components) {
        Set<String> fields = new HashSet<>();
        for (TemplateComponentDTO component : components) {
            if (!fields.add(component.getField())) {
                throw new BusinessException("模板字段标识不能重复：" + component.getField());
            }

            MaterialHandler handler = materialHandlerFactory.getHandler(component.getType());
            handler.validate(component);
        }
    }

    private void saveMaterials(Long templateId, List<TemplateComponentDTO> components) {
        for (int i = 0; i < components.size(); i++) {
            TemplateComponentDTO component = components.get(i);
            MaterialHandler handler = materialHandlerFactory.getHandler(component.getType());

            Material material = new Material();
            material.setTemplateId(templateId);
            material.setType(component.getType());
            material.setField(component.getField());
            material.setLabel(component.getLabel());
            material.setRequired(Boolean.TRUE.equals(component.getRequired()) ? 1 : 0);
            material.setSortOrder(i);
            material.setConfig(toJson(handler.buildConfig(component)));
            materialMapper.insert(material);
        }
    }

    private List<TemplateComponentVO> listComponentVOs(Long templateId) {
        List<Material> materials = materialMapper.selectList(
                new LambdaQueryWrapper<Material>()
                        .eq(Material::getTemplateId, templateId)
                        .orderByAsc(Material::getSortOrder)
        );

        List<TemplateComponentVO> components = new ArrayList<>();
        for (Material material : materials) {
            TemplateComponentVO component = templateConvert.toComponentVO(material);
            component.setConfig(parseConfig(material.getConfig()));
            components.add(component);
        }
        return components;
    }

    private String toJson(Map<String, Object> config) {
        try {
            return objectMapper.writeValueAsString(config);
        } catch (Exception e) {
            throw new BusinessException("物料配置序列化失败");
        }
    }

    private Map<String, Object> parseConfig(String config) {
        try {
            if (!StringUtils.hasText(config)) {
                return Map.of();
            }
            return objectMapper.readValue(config, new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new BusinessException("物料配置解析失败");
        }
    }

    private long positiveOrDefault(Integer value, long defaultValue) {
        return value == null || value <= 0 ? defaultValue : value;
    }

    private void clearTemplateRuntimeCache(Long templateId) {
        redisUtil.delete(RedisKeyConstant.MATERIAL_CACHE_KEY_PREFIX + templateId);
    }
}
