package com.labelhub.common.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "labelhub.minio")
public class MinioProperties {

    private String endpoint;

    private String publicUrl;

    private String accessKey;

    private String secretKey;
}
