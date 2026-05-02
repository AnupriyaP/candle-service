package com.candleservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    public static final String TOPIC_BID_ASK = "market.bidask";

    /**
     * Creates the topic automatically if it does not exist.
     * Spring Kafka's KafkaAdmin picks this bean up on startup
     * and creates the topic on the broker.
     *
     * partitions = 6  — one per symbol with room to grow
     * replicas   = 1  — single broker setup (local dev)
     */
    @Bean
    public NewTopic bidAskTopic() {
        return TopicBuilder.name(TOPIC_BID_ASK)
                .partitions(6)
                .replicas(1)
                .build();
    }
}