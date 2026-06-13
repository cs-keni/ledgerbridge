package com.ledgerbridge.common.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@Profile("!demo")
public class KafkaConfig {

    public static final String TRANSACTION_EVENTS_TOPIC = "transaction-events";

    @Bean
    public NewTopic transactionEventsTopic() {
        return TopicBuilder.name(TRANSACTION_EVENTS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    // @RetryableTopic uses autoCreateTopics=false — must provision retry and DLT
    // topics explicitly so they exist on brokers with auto.create.topics.enable=false.
    @Bean
    public NewTopic transactionEventsRetry0Topic() {
        return TopicBuilder.name(TRANSACTION_EVENTS_TOPIC + "-retry-0")
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic transactionEventsRetry1Topic() {
        return TopicBuilder.name(TRANSACTION_EVENTS_TOPIC + "-retry-1")
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic transactionEventsDltTopic() {
        return TopicBuilder.name(TRANSACTION_EVENTS_TOPIC + "-dlt")
                .partitions(1)
                .replicas(1)
                .build();
    }
}
