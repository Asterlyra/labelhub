package com.labelhub.common.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Data
@Component
@ConfigurationProperties(prefix = "labelhub.agent")
public class AgentProperties {

    private String token = "";

    private String baseUrl = "http://127.0.0.1:5001";

    public String normalizedBaseUrl() {
        if (!StringUtils.hasText(baseUrl)) {
            return "http://127.0.0.1:5001";
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
