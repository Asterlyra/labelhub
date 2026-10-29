package com.labelhub.modules.dataset.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

/**
 * 数据集上传请求体。
 */
@Data
public class DatasetUploadDTO {

    @NotBlank(message = "数据集名称不能为空")
    @Size(max = 200, message = "数据集名称不能超过200个字符")
    private String name;

    private String description;

    @NotNull(message = "上传文件不能为空")
    private MultipartFile file;

    /**
     * 字段映射配置，JSON 字符串，格式为 {"fields":[{"sourceName":"原字段","targetName":"新字段","enabled":true}]}。
     */
    private String schema;
}
