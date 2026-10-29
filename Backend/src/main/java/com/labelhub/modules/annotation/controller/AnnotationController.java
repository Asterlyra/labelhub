package com.labelhub.modules.annotation.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelhub.common.annotation.RateLimit;
import com.labelhub.common.exception.BusinessException;
import com.labelhub.common.result.R;
import com.labelhub.modules.annotation.dto.AnnotationSaveDTO;
import com.labelhub.modules.annotation.service.AnnotationService;
import com.labelhub.modules.annotation.vo.AnnotationInitVO;
import com.labelhub.modules.annotation.vo.AnnotationSuggestionVO;
import com.labelhub.modules.annotation.vo.ItemDetailVO;
import com.labelhub.modules.task.dto.TaskMarketplaceQueryDTO;
import com.labelhub.modules.task.service.TaskService;
import com.labelhub.modules.task.vo.LabelerTaskVO;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

/**
 * 标注工作台接口。
 */
@RestController
@RequestMapping("/api/annotation")
@RequiredArgsConstructor
public class AnnotationController {

    private final AnnotationService annotationService;
    private final TaskService taskService;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    /**
     * 查询当前标注员的标注任务列表。
     */
    @SaCheckRole("LABELER")
    @GetMapping("/tasks")
    public R<Page<LabelerTaskVO>> listMyTasks(TaskMarketplaceQueryDTO dto) {
        return R.ok(taskService.listMyMarketplaceTasks(dto));
    }

    /**
     * 初始化标注工作台。
     */
    @SaCheckRole("LABELER")
    @GetMapping("/workspace/{taskId}")
    public R<AnnotationInitVO> initWorkspace(@PathVariable Long taskId) {
        return R.ok(annotationService.initWorkspace(taskId));
    }

    /**
     * 查询单题详情。
     */
    @SaCheckRole("LABELER")
    @GetMapping("/item/{taskItemId}")
    public R<ItemDetailVO> itemDetail(@PathVariable Long taskItemId) {
        return R.ok(annotationService.getItemDetail(taskItemId));
    }

    /**
     * 创建 AI 辅助标注建议。
     */
    @SaCheckRole("LABELER")
    @RateLimit(dimensions = RateLimit.Dimension.GLOBAL, count = 60, interval = 1, timeUnit = RateLimit.TimeUnit.MINUTES)
    @RateLimit(dimensions = RateLimit.Dimension.USER, count = 5, interval = 1, timeUnit = RateLimit.TimeUnit.MINUTES)
    @PostMapping("/item/{taskItemId}/ai-suggestion")
    public R<AnnotationSuggestionVO> createAiSuggestion(@PathVariable Long taskItemId) {
        return R.ok(annotationService.createAiSuggestion(taskItemId));
    }

    /**
     * 查询 AI 辅助标注建议。
     */
    @SaCheckRole("LABELER")
    @GetMapping("/item/{taskItemId}/ai-suggestion/{suggestionId}")
    public R<AnnotationSuggestionVO> queryAiSuggestion(@PathVariable Long taskItemId,
                                                       @PathVariable String suggestionId) {
        return R.ok(annotationService.queryAiSuggestion(taskItemId, suggestionId));
    }

    /**
     * 保存草稿。
     */
    @SaCheckRole("LABELER")
    @RateLimit(dimensions = RateLimit.Dimension.USER, count = 60, interval = 1, timeUnit = RateLimit.TimeUnit.MINUTES)
    @PostMapping("/item/{taskItemId}/draft")
    public R<Void> saveDraft(@PathVariable Long taskItemId, @Valid @RequestBody AnnotationSaveDTO dto) {
        annotationService.saveDraft(taskItemId, dto);
        return R.ok();
    }

    /**
     * 保存草稿，并处理图片/文件上传物料。
     */
    @SaCheckRole("LABELER")
    @PostMapping(value = "/item/{taskItemId}/draft", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<Void> saveDraftWithFiles(@PathVariable Long taskItemId,
                                      @RequestParam("annotation") String annotation,
                                      @RequestPart(value = "files", required = false) List<MultipartFile> files,
                                      @RequestParam(value = "fileFields", required = false) List<String> fileFields) {
        AnnotationSaveDTO dto = parseAnnotation(annotation);
        annotationService.saveDraft(taskItemId, dto, buildFileMap(files, fileFields));
        return R.ok();
    }

    /**
     * 提交标注。
     */
    @SaCheckRole("LABELER")
    @RateLimit(dimensions = RateLimit.Dimension.GLOBAL, count = 300, interval = 1, timeUnit = RateLimit.TimeUnit.MINUTES)
    @RateLimit(dimensions = RateLimit.Dimension.USER, count = 30, interval = 1, timeUnit = RateLimit.TimeUnit.MINUTES)
    @PostMapping(value = "/item/{taskItemId}/submit", consumes = MediaType.APPLICATION_JSON_VALUE)
    public R<Void> submit(@PathVariable Long taskItemId, @Valid @RequestBody AnnotationSaveDTO dto) {
        annotationService.submit(taskItemId, dto);
        return R.ok();
    }

    /**
     * 提交标注，并处理图片/文件上传物料。
     */
    @SaCheckRole("LABELER")
    @RateLimit(dimensions = RateLimit.Dimension.GLOBAL, count = 300, interval = 1, timeUnit = RateLimit.TimeUnit.MINUTES)
    @RateLimit(dimensions = RateLimit.Dimension.USER, count = 30, interval = 1, timeUnit = RateLimit.TimeUnit.MINUTES)
    @PostMapping(value = "/item/{taskItemId}/submit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<Void> submitWithFiles(@PathVariable Long taskItemId,
                                   @RequestParam("annotation") String annotation,
                                   @RequestPart(value = "files", required = false) List<MultipartFile> files,
                                   @RequestParam(value = "fileFields", required = false) List<String> fileFields) {
        AnnotationSaveDTO dto = parseAnnotation(annotation);
        annotationService.submit(taskItemId, dto, buildFileMap(files, fileFields));
        return R.ok();
    }

    /**
     * 解析标注提交 JSON。
     */
    private AnnotationSaveDTO parseAnnotation(String annotation) {
        try {
            AnnotationSaveDTO dto = objectMapper.readValue(annotation, AnnotationSaveDTO.class);
            validate(dto);
            return dto;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("标注 JSON 解析失败：" + e.getMessage());
        }
    }

    /**
     * 校验标注提交参数。
     */
    private void validate(AnnotationSaveDTO dto) {
        Set<ConstraintViolation<AnnotationSaveDTO>> violations = validator.validate(dto);
        if (!violations.isEmpty()) {
            throw new BusinessException(violations.iterator().next().getMessage());
        }
    }

    /**
     * 构建字段与上传文件的映射关系。
     */
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
