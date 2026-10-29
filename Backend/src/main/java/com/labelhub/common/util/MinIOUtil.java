package com.labelhub.common.util;

import com.labelhub.common.exception.BusinessException;
import com.labelhub.common.properties.MinioProperties;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class MinIOUtil {

    private static final DateTimeFormatter DATE_PATH_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;

    public UploadResult upload(MultipartFile file, String bucket, String directory) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }
        if (!StringUtils.hasText(bucket)) {
            throw new BusinessException("MinIO bucket 不能为空");
        }

        String objectName = buildObjectName(file.getOriginalFilename(), directory);
        String contentType = StringUtils.hasText(file.getContentType())
                ? file.getContentType()
                : "application/octet-stream";

        try {
            ensureBucketExists(bucket);
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(contentType)
                    .build());
            return new UploadResult(bucket, objectName, buildFileUrl(bucket, objectName), contentType,
                    file.getOriginalFilename(), file.getSize());
        } catch (Exception e) {
            throw new BusinessException("文件上传到 MinIO 失败：" + e.getMessage());
        }
    }

    public UploadResult uploadBytes(byte[] bytes, String originalFilename, String contentType, String bucket, String directory) {
        if (bytes == null || bytes.length == 0) {
            throw new BusinessException("上传内容不能为空");
        }
        if (!StringUtils.hasText(bucket)) {
            throw new BusinessException("MinIO bucket 不能为空");
        }

        String objectName = buildObjectName(originalFilename, directory);
        String actualContentType = StringUtils.hasText(contentType) ? contentType : "application/octet-stream";

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes)) {
            ensureBucketExists(bucket);
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(inputStream, bytes.length, -1)
                    .contentType(actualContentType)
                    .build());
            return new UploadResult(bucket, objectName, buildFileUrl(bucket, objectName), actualContentType,
                    originalFilename, bytes.length);
        } catch (Exception e) {
            throw new BusinessException("内容上传到 MinIO 失败：" + e.getMessage());
        }
    }

    public String buildFileUrl(String bucket, String objectName) {
        String baseUrl = resolvePublicBaseUrl();
        return trimTrailingSlash(baseUrl) + "/" + trimSlashes(bucket) + "/" + trimSlashes(objectName);
    }

    private String resolvePublicBaseUrl() {
        String baseUrl = StringUtils.hasText(minioProperties.getPublicUrl())
                ? minioProperties.getPublicUrl()
                : minioProperties.getEndpoint();
        if (!StringUtils.hasText(baseUrl)) {
            throw new BusinessException("MinIO 公开访问地址不能为空");
        }

        URI uri;
        try {
            uri = URI.create(baseUrl);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("MinIO 公开访问地址不合法：" + baseUrl);
        }
        if (!uri.isAbsolute() || !StringUtils.hasText(uri.getScheme()) || !StringUtils.hasText(uri.getHost())) {
            throw new BusinessException("MinIO 公开访问地址必须是完整 URL，例如：http://127.0.0.1:9000");
        }
        return baseUrl;
    }

    private void ensureBucketExists(String bucket) throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }

    private String buildObjectName(String originalFilename, String directory) {
        String safeDirectory = StringUtils.hasText(directory) ? trimSlashes(directory) : "files";
        String extension = getExtension(originalFilename);
        return safeDirectory + "/" + LocalDate.now().format(DATE_PATH_FORMATTER) + "/" + UUID.randomUUID() + extension;
    }

    private String getExtension(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            return "";
        }
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == originalFilename.length() - 1) {
            return "";
        }
        return originalFilename.substring(dotIndex);
    }

    private String trimSlashes(String value) {
        return value.replaceAll("^/+", "").replaceAll("/+$", "");
    }

    private String trimTrailingSlash(String value) {
        return value.replaceAll("/+$", "");
    }

    public record UploadResult(String bucket, String objectName, String url, String contentType,
                               String originalFilename, long size) {
    }
}
