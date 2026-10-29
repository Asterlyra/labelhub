package com.labelhub.modules.task.strategy;

import com.labelhub.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 任务分发策略工厂。
 */
@Component
public class DistributionStrategyFactory {

    private final Map<String, DistributionStrategy> strategyMap;

    public DistributionStrategyFactory(List<DistributionStrategy> strategies) {
        this.strategyMap = strategies.stream().collect(Collectors.toMap(DistributionStrategy::strategyType, Function.identity()));
    }

    /**
     * 根据策略类型获取分发策略。
     */
    public DistributionStrategy getStrategy(String strategyType) {
        DistributionStrategy strategy = strategyMap.get(strategyType);
        if (strategy == null) {
            throw new BusinessException("不支持的分发策略：" + strategyType);
        }
        return strategy;
    }
}
