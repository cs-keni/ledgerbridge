package com.ledgerbridge.risk.consumer;

import com.ledgerbridge.common.config.KafkaConfig;
import com.ledgerbridge.risk.engine.RiskEngine;
import com.ledgerbridge.risk.engine.RiskScoringResult;
import com.ledgerbridge.risk.metrics.RiskMetrics;
import com.ledgerbridge.risk.model.CustomerRiskProfile;
import com.ledgerbridge.risk.model.ProcessedTransactionEvent;
import com.ledgerbridge.risk.repository.ProcessedTransactionEventRepository;
import com.ledgerbridge.risk.service.CustomerRiskProfileService;
import com.ledgerbridge.transaction.event.TransactionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionRiskConsumer {

    private final RiskEngine riskEngine;
    private final CustomerRiskProfileService profileService;
    private final ProcessedTransactionEventRepository processedRepo;
    private final RiskMetrics riskMetrics;

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            autoCreateTopics = "false"
    )
    @KafkaListener(topics = KafkaConfig.TRANSACTION_EVENTS_TOPIC)
    public void consume(TransactionEvent event,
                        @Header(name = "X-Correlation-ID", required = false) String correlationId) {
        if (correlationId != null) MDC.put("correlationId", correlationId);
        try {
            // T10: full idempotency — skip ALL re-evaluations (not just alerted ones)
            if (processedRepo.existsByTransactionId(event.transactionId())) {
                log.debug("Risk event already processed for txn={}, skipping", event.transactionId());
                return;
            }

            CustomerRiskProfile profile = profileService.getOrCreate(event.userId());

            // D19: score-first evaluation — evaluate before any profile mutation
            RiskScoringResult result = riskEngine.evaluate(event, profile);

            // D19: baseline-poisoning protection — only update profile if transaction is clean
            if (!result.alertTriggered()) {
                profileService.updateProfile(profile, event);
            }

            // TODOS.md Phase 5: persist latest score/tier regardless of alert status
            profileService.saveRiskScore(profile, result.finalScore());

            // Mark evaluated — idempotency guard for retries/redeliveries
            try {
                processedRepo.save(new ProcessedTransactionEvent(event.transactionId()));
            } catch (DataIntegrityViolationException ignored) {
                // Concurrent retry beat us — already processed, result is the same
            }

            log.info("Risk evaluation: txn={} score={} alert={} severity={}",
                    event.transactionId(), String.format("%.3f", result.finalScore()),
                    result.alertTriggered(),
                    result.alert() != null ? result.alert().getSeverity() : "none");
        } finally {
            MDC.remove("correlationId");
        }
    }

    @DltHandler
    public void handleDlt(TransactionEvent event,
                          @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.error("TransactionEvent reached DLT after all retries: txn={} topic={}",
                event.transactionId(), topic);
        riskMetrics.incrementDlt();
    }
}
