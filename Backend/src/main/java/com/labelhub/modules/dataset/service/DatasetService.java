package com.labelhub.modules.dataset.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.labelhub.modules.dataset.dto.DatasetItemQueryDTO;
import com.labelhub.modules.dataset.dto.DatasetQueryDTO;
import com.labelhub.modules.dataset.dto.DatasetUploadDTO;
import com.labelhub.modules.dataset.entity.Dataset;
import com.labelhub.modules.dataset.vo.DatasetDetailVO;
import com.labelhub.modules.dataset.vo.DatasetFieldPreviewVO;
import com.labelhub.modules.dataset.vo.DatasetItemVO;
import com.labelhub.modules.dataset.vo.DatasetVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 数据集服务接口。
 */
public interface DatasetService extends IService<Dataset> {

    /**
     * 上传并解析数据集文件。
     */
    DatasetVO uploadDataset(DatasetUploadDTO dto);

    /**
     * 预览数据集源文件字段。
     */
    DatasetFieldPreviewVO previewFields(MultipartFile file);

    /**
     * 分页查询数据集。
     */
    Page<DatasetVO> listDatasets(DatasetQueryDTO dto);

    /**
     * 分页查询当前登录用户创建的数据集。
     */
    Page<DatasetVO> listMyDatasets(DatasetQueryDTO dto);

    /**
     * 查询数据集详情。
     */
    DatasetDetailVO getDatasetDetail(Long id);

    /**
     * 分页查询数据项。
     */
    Page<DatasetItemVO> listDatasetItems(Long datasetId, DatasetItemQueryDTO dto);

    /**
     * 删除数据集及其数据项。
     */
    void deleteDataset(Long id);
}
