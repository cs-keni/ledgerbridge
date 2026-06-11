package com.ledgerbridge.transaction.kafka;

import com.ledgerbridge.common.config.KafkaConfig;
import com.ledgerbridge.transaction.event.TransactionCompletedEvent;
import com.ledgerbridge.transaction.event.TransactionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionEventProducer {

    private final KafkaTemplate<String, TransactionEvent> kafkaTemplate;

    // Fires after the DB transaction commits (decision D10).
    // If Kafka publish fails the DB is already committed — accepted gap (decision D2).
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTransactionCompleted(TransactionCompletedEvent event) {
        TransactionEvent payload = event.payload();
        Message<TransactionEvent> message = MessageBuilder
                .withPayload(payload)
                .setHeader(KafkaHeaders.TOPIC, KafkaConfig.TRANSACTION_EVENTS_TOPIC)
                .setHeader(KafkaHeaders.KEY, payload.userId().toString())
                .setHeader("X-Correlation-ID",
                        payload.correlationId() != null ? payload.correlationId() : "")
                .build();
        kafkaTemplate.send(message).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish TransactionEvent: transactionId={} correlationId={}",
                        payload.transactionId(), payload.correlationId(), ex);
            }
        });
    }
}
