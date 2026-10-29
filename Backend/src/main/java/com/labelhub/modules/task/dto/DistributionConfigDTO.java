package com.labelhub.modules.task.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

import java.util.List;

/**
 * 任务分发策略配置请求体。
 */
@Data
public class DistributionConfigDTO {

    private String strategyType = "FIRST_COME_FIRST_SERVED";

    @Min(value = 1, message = "每人最大领取数量必须大于0")
    private Integer maxClaimPerUser = 10;

    @Min(value = 1, message = "单次领取数量必须大于0")
    private Integer claimCount = 1;

    /**
     * 指派策略下参与平分任务配额的标注员 ID 列表。
     */
    private List<Long> labelerIds;
}
