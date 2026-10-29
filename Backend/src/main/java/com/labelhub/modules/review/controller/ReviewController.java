package com.labelhub.modules.review.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelhub.common.annotation.RateLimit;
import com.labelhub.common.constant.RoleConstant;
import com.labelhub.common.exception.BusinessException;
import com.labelhub.common.result.R;
import com.labelhub.modules.review.dto.ReviewModifyDTO;
import com.labelhub.modules.review.dto.ReviewRejectDTO;
import com.labelhub.modules.review.dto.ReviewTaskQueryDTO;
import com.labelhub.modules.review.service.ReviewService;
import com.labelhub.modules.review.vo.ReviewItemDetailVO;
import com.labelhub.modules.review.vo.ReviewTaskVO;
import com.labelhub.modules.review.vo.ReviewWorkspaceInitVO;
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
 * 人工审核接口。
 */
@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    /**
     * 查询我的审核任务列表。
     */
    @SaCheckRole(RoleConstant.REVIEWER)
    @GetMapping("/tasks")
    public R<Page<ReviewTaskVO>> listMyTasks(ReviewTaskQueryDTO dto) {
        return R.ok(reviewService.listMyTasks(dto));
    }

    /**
     * 初始化审核工作台。
     */
    @SaCheckRole(RoleConstant.REVIEWER)
    @GetMapping("/tasks/{taskId}/workspace")
    public R<ReviewWorkspaceInitVO> initWorkspace(@PathVariable Long taskId) {
        return R.ok(reviewService.initWorkspace(taskId));
    }

    /**
     * 查询审核项详情。
     */
    @SaCheckRole(RoleConstant.REVIEWER)
    @GetMapping("/items/{taskItemId}")
    public R<ReviewItemDetailVO> getItemDetail(@PathVariable Long taskItemId) {
        return R.ok(reviewService.getItemDetail(taskItemId));
    }

    /**
     * 查询任务 AI 预审结果列表。
     */
    @SaCheckRole(RoleConstant.REVIEWER)
    @GetMapping("/tasks/{taskId}/ai-audit-results")
    public R<JsonNode> listAiAuditResults(@PathVariable Long taskId) {
        return R.ok(reviewService.listAiAuditResults(taskId));
    }

    /**
     * 查询任务项 AI 预审结果。
     */
    @SaCheckRole(RoleConstant.REVIEWER)
    @GetMapping("/items/{taskItemId}/ai-audit-result")
    public R<JsonNode> getAiAuditResult(@PathVariable Long taskItemId) {
        return R.ok(reviewService.getAiAuditResult(taskItemId));
    }

    /**
     * 审核通过。
     */
    @SaCheckRole(RoleConstant.REVIEWER)
    @RateLimit(dimensions = RateLimit.Dimension.GLOBAL, count = 500, interval = 1, timeUnit = RateLimit.TimeUnit.MINUTES)
    @RateLimit(dimensions = RateLimit.Dimension.USER, count = 60, interval = 1, timeUnit = RateLimit.TimeUnit.MINUTES)
    @PostMapping("/items/{taskItemId}/approve")
    public R<Void> approve(@PathVariable Long taskItemId) {
        return handleVoidAction(() -> reviewService.approve(taskItemId));
    }

    /**
     * 审核驳回。
     */
    @SaCheckRole(RoleConstant.REVIEWER)
    @RateLimit(dimensions = RateLimit.Dimension.GLOBAL, count = 500, interval = 1, timeUnit = RateLimit.TimeUnit.MINUTES)
    @RateLimit(dimensions = RateLimit.Dimension.USER, count = 60, interval = 1, timeUnit = RateLimit.TimeUnit.MINUTES)
    @PostMapping("/items/{taskItemId}/reject")
    public R<Void> reject(@PathVariable Long taskItemId, @Valid @RequestBody ReviewRejectDTO dto) {
        return handleVoidAction(() -> reviewService.reject(taskItemId, dto));
    }

    /**
     * 修改标注数据并审核通过。
     */
    @SaCheckRole(RoleConstant.REVIEWER)
    @RateLimit(dimensions = RateLimit.Dimension.GLOBAL, count = 500, interval = 1, timeUnit = RateLimit.TimeUnit.MINUTES)
    @RateLimit(dimensions = RateLimit.Dimension.USER, count = 60, interval = 1, timeUnit = RateLimit.TimeUnit.MINUTES)
    @PostMapping(value = "/items/{taskItemId}/modify", consumes = MediaType.APPLICATION_JSON_VALUE)
    public R<Void> modify(@PathVariable Long taskItemId, @Valid @RequestBody ReviewModifyDTO dto) {
        return handleVoidAction(() -> reviewService.modify(taskItemId, dto));
    }

    /**
     * 修改标注数据并审核通过，同时处理图片/文件上传物料。
     */
    @SaCheckRole(RoleConstant.REVIEWER)
    @RateLimit(dimensions = RateLimit.Dimension.GLOBAL, count = 500, interval = 1, timeUnit = RateLimit.TimeUnit.MINUTES)
    @RateLimit(dimensions = RateLimit.Dimension.USER, count = 60, interval = 1, timeUnit = RateLimit.TimeUnit.MINUTES)
    @PostMapping(value = "/items/{taskItemId}/modify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<Void> modifyWithFiles(@PathVariable Long taskItemId,
                                   @RequestParam("review") String review,
                                   @RequestPart(value = "files", required = false) List<MultipartFile> files,
                                   @RequestParam(value = "fileFields", required = false) List<String> fileFields) {
        ReviewModifyDTO dto = parseReviewModify(review);
        return handleVoidAction(() -> reviewService.modify(taskItemId, dto, buildFileMap(files, fileFields)));
    }

    /**
     * 统一处理无返回值接口。
     */
    private R<Void> handleVoidAction(Runnable runnable) {
        runnable.run();
        return R.ok();
    }

    /**
     * 解析审核修改 JSON。
     */
    private ReviewModifyDTO parseReviewModify(String review) {
        try {
            ReviewModifyDTO dto = objectMapper.readValue(review, ReviewModifyDTO.class);
            validate(dto);
            return dto;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("审核修改 JSON 解析失败：" + e.getMessage());
        }
    }

    /**
     * 校验审核修改参数。
     */
    private void validate(ReviewModifyDTO dto) {
        Set<ConstraintViolation<ReviewModifyDTO>> violations = validator.validate(dto);
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
