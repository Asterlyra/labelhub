package com.labelhub.common.service;

import com.labelhub.modules.template.entity.Material;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 物料上传服务。
 */
public interface MaterialUploadService {

    /**
     * 处理标注结果中的图片/文件上传，并将上传后的 URL 回填到标注数据中。
     */
    Map<String, Object> fillSubmissionUploadUrls(List<Material> materials,
                                                 Map<String, Object> annotationData,
                                                 Map<String, MultipartFile> componentFiles);
}
