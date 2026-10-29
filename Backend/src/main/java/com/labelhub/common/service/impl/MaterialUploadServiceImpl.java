package com.labelhub.common.service.impl;

import com.labelhub.common.constant.MinioBucketConstant;
import com.labelhub.common.constant.MinioObjectDirectoryConstant;
import com.labelhub.common.exception.BusinessException;
import com.labelhub.common.service.MaterialUploadService;
import com.labelhub.common.util.MinIOUtil;
import com.labelhub.modules.template.entity.Material;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 物料上传服务实现。
 */
@Service
@RequiredArgsConstructor
public class MaterialUploadServiceImpl implements MaterialUploadService {

    private static final String IMAGE_UPLOAD_TYPE = "imageUpload";
    private static final String FILE_UPLOAD_TYPE = "fileUpload";

    private final MinIOUtil minIOUtil;

    @Override
    public Map<String, Object> fillSubmissionUploadUrls(List<Material> materials,
                                                        Map<String, Object> annotationData,
                                                        Map<String, MultipartFile> componentFiles) {
        Map<String, Object> data = annotationData == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(annotationData);
        if (componentFiles == null || componentFiles.isEmpty()) {
            return data;
        }

        Map<String, Material> materialMap = materials.stream()
                .collect(Collectors.toMap(Material::getField, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        for (Map.Entry<String, MultipartFile> entry : componentFiles.entrySet()) {
            String field = entry.getKey();
            Material material = materialMap.get(field);
            if (material == null) {
                throw new BusinessException("上传文件未匹配到模板物料字段：" + field);
            }
            if (!isUploadMaterial(material)) {
                throw new BusinessException("字段不是图片或文件上传物料，不能绑定文件：" + field);
            }

            MinIOUtil.UploadResult uploadResult = uploadMaterialFile(material, entry.getValue());
            data.put(field, uploadResult.url());
        }
        return data;
    }

    /**
     * 上传图片或文件物料。
     */
    private MinIOUtil.UploadResult uploadMaterialFile(Material material, MultipartFile file) {
        if (IMAGE_UPLOAD_TYPE.equals(material.getType())) {
            validateImageFile(material, file);
            return minIOUtil.upload(file, MinioBucketConstant.ANNOTATION, MinioObjectDirectoryConstant.IMAGES);
        }
        return minIOUtil.upload(file, MinioBucketConstant.ANNOTATION, MinioObjectDirectoryConstant.FILES);
    }

    /**
     * 校验图片物料上传文件类型。
     */
    private void validateImageFile(Material material, MultipartFile file) {
        String contentType = file.getContentType();
        if (!StringUtils.hasText(contentType) || !contentType.startsWith("image/")) {
            throw new BusinessException(material.getField() + " 只能上传图片文件");
        }
    }

    /**
     * 判断当前物料是否为图片或文件上传类型。
     */
    private boolean isUploadMaterial(Material material) {
        return IMAGE_UPLOAD_TYPE.equals(material.getType()) || FILE_UPLOAD_TYPE.equals(material.getType());
    }
}
