package com.labelhub.modules.task.vo;

import lombok.Data;

import java.util.Map;

/**
 * 任务分发配置响应对象。
 */
@Data
public class TaskDistributionConfigVO {

    private String strategyType;

    private Map<String, Object> config;
}
