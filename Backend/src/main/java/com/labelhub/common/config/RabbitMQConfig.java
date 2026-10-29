package com.labelhub.common.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String AI_REVIEW_QUEUE = "ai.review.queue";
    public static final String AI_REVIEW_EXCHANGE = "ai.review.exchange";
    public static final String AI_REVIEW_ROUTING_KEY = "ai.review";

    @Bean
    public Queue aiReviewQueue() {
        return new Queue(AI_REVIEW_QUEUE, true);
    }

    @Bean
    public DirectExchange aiReviewExchange() {
        return new DirectExchange(AI_REVIEW_EXCHANGE);
    }

    @Bean
    public Binding aiReviewBinding(Queue aiReviewQueue, DirectExchange aiReviewExchange) {
        return BindingBuilder.bind(aiReviewQueue).to(aiReviewExchange).with(AI_REVIEW_ROUTING_KEY);
    }
}
