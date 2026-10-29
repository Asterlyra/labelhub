package com.labelhub.modules.annotation.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelhub.common.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * AI预审消息发送者。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AIReviewProducer {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 发送AI预审消息到MQ。
     */
    public void sendAIReviewMessage(AIReviewMessage message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.AI_REVIEW_EXCHANGE,
                    RabbitMQConfig.AI_REVIEW_ROUTING_KEY,
                    json
            );
            log.info("发送AI预审消息成功：taskItemId={}, taskId={}", message.getTaskItemId(), message.getTaskId());
        } catch (Exception e) {
            log.error("发送AI预审消息失败：taskItemId={}, 错误={}", message.getTaskItemId(), e.getMessage(), e);
        }
    }
}
