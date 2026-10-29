package com.labelhub.modules.dataset.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labelhub.common.annotation.RateLimit;
import com.labelhub.common.result.R;
import com.labelhub.modules.dataset.dto.DatasetItemQueryDTO;
import com.labelhub.modules.dataset.dto.DatasetQueryDTO;
import com.labelhub.modules.dataset.dto.DatasetUploadDTO;
import com.labelhub.modules.dataset.service.DatasetService;
import com.labelhub.modules.dataset.vo.DatasetDetailVO;
import com.labelhub.modules.dataset.vo.DatasetFieldPreviewVO;
import com.labelhub.modules.dataset.vo.DatasetItemVO;
import com.labelhub.modules.dataset.vo.DatasetVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 数据集管理接口。
 */
@RestController
@RequestMapping("/api/datasets")
@RequiredArgsConstructor
public class DatasetController {

    private final DatasetService datasetService;

    /**
     * 上传数据集文件并触发解析。
     */
    @SaCheckRole("PROJECT_ADMIN")
    @RateLimit(dimensions = RateLimit.Dimension.GLOBAL, count = 60, interval = 1, timeUnit = RateLimit.TimeUnit.MINUTES)
    @RateLimit(dimensions = RateLimit.Dimension.USER, count = 5, interval = 1, timeUnit = RateLimit.TimeUnit.MINUTES)
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<DatasetVO> upload(@Valid @ModelAttribute DatasetUploadDTO dto) {
        return R.ok(datasetService.uploadDataset(dto));
    }

    /**
     * 预览数据集文件字段。
     */
    @SaCheckRole("PROJECT_ADMIN")
    @RateLimit(dimensions = RateLimit.Dimension.GLOBAL, count = 120, interval = 1, timeUnit = RateLimit.TimeUnit.MINUTES)
    @RateLimit(dimensions = RateLimit.Dimension.USER, count = 10, interval = 1, timeUnit = RateLimit.TimeUnit.MINUTES)
    @PostMapping(value = "/preview-fields", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<DatasetFieldPreviewVO> previewFields(@RequestParam("file") MultipartFile file) {
        return R.ok(datasetService.previewFields(file));
    }

    /**
     * 分页查询数据集列表。
     */
    @SaCheckRole("PROJECT_ADMIN")
    @GetMapping
    public R<Page<DatasetVO>> list(DatasetQueryDTO dto) {
        return R.ok(datasetService.listDatasets(dto));
    }

    /**
     * 分页查询当前登录用户创建的数据集。
     */
    @SaCheckRole("PROJECT_ADMIN")
    @GetMapping("/my")
    public R<Page<DatasetVO>> listMine(DatasetQueryDTO dto) {
        return R.ok(datasetService.listMyDatasets(dto));
    }

    /**
     * 查询数据集详情。
     */

    @SaCheckRole("PROJECT_ADMIN")
    @GetMapping("/{id}")
    public R<DatasetDetailVO> detail(@PathVariable Long id) {
        return R.ok(datasetService.getDatasetDetail(id));
    }

    /**
     * 分页查询数据集数据项。
     */
    @SaCheckRole("PROJECT_ADMIN")
    @GetMapping("/{id}/items")
    public R<Page<DatasetItemVO>> listItems(@PathVariable Long id, DatasetItemQueryDTO dto) {
        return R.ok(datasetService.listDatasetItems(id, dto));
    }

    /**
     * 删除数据集。
     */
    @SaCheckRole("PROJECT_ADMIN")
    @RateLimit(dimensions = RateLimit.Dimension.USER, count = 20, interval = 1, timeUnit = RateLimit.TimeUnit.MINUTES)
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        datasetService.deleteDataset(id);
        return R.ok();
    }
}
