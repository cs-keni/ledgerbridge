package com.ledgerbridge.risk.service;

import com.ledgerbridge.account.model.Account;
import com.ledgerbridge.account.repository.AccountRepository;
import com.ledgerbridge.audit.model.AuditAction;
import com.ledgerbridge.common.audit.AuditLog;
import com.ledgerbridge.common.exception.AppException;
import com.ledgerbridge.risk.dto.AlertDetailResponse;
import com.ledgerbridge.risk.dto.AlertReviewRequest;
import com.ledgerbridge.risk.dto.RiskAlertResponse;
import com.ledgerbridge.risk.model.AlertSeverity;
import com.ledgerbridge.risk.model.AlertStatus;
import com.ledgerbridge.risk.model.AlertType;
import com.ledgerbridge.risk.model.RiskAlert;
import com.ledgerbridge.risk.repository.RiskAlertRepository;
import com.ledgerbridge.transaction.event.TransactionEvent;
import com.ledgerbridge.transaction.model.LedgerTransaction;
import com.ledgerbridge.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {

    private final RiskAlertRepository riskAlertRepository;
    private final SseAlertService sseAlertService;
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;

    @Transactional
    public RiskAlert createAlert(TransactionEvent event, AlertType alertType,
                                 AlertSeverity severity, double score,
                                 Map<String, Object> ruleDetails) {
        try {
            RiskAlert alert = new RiskAlert();
            alert.setAlertNumber(generateAlertNumber());
            alert.setTransactionId(event.transactionId());
            alert.setUserId(event.userId());
            alert.setAlertType(alertType);
            alert.setSeverity(severity);
            alert.setRiskScore(score);
            alert.setRuleDetails(ruleDetails);
            RiskAlert saved = riskAlertRepository.save(alert);
            sseAlertService.broadcast(RiskAlertResponse.from(saved));
            return saved;
        } catch (DataIntegrityViolationException e) {
            // Concurrent Kafka retry created the alert before us — fetch and return it
            log.debug("Duplicate alert creation for txn={} — returning existing alert", event.transactionId());
            return riskAlertRepository.findByTransactionId(event.transactionId())
                    .orElseThrow(() -> new IllegalStateException("Alert missing after concurrent insert", e));
        }
    }

    @Transactional(readOnly = true)
    public AlertDetailResponse getAlertById(UUID alertId) {
        RiskAlert alert = riskAlertRepository.findById(alertId)
                .orElseThrow(() -> new AppException("Alert not found: " + alertId, HttpStatus.NOT_FOUND));
        LedgerTransaction tx = transactionRepository.findById(alert.getTransactionId())
                .orElseThrow(() -> new AppException("Transaction not found for alert: " + alertId, HttpStatus.NOT_FOUND));
        Account account = accountRepository.findById(tx.getAccountId()).orElse(null);
        return AlertDetailResponse.from(alert, tx, account);
    }

    @Transactional(readOnly = true)
    public Page<RiskAlertResponse> getAlerts(Pageable pageable) {
        return riskAlertRepository.findAll(pageable).map(RiskAlertResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<RiskAlertResponse> getOpenAlerts(Pageable pageable) {
        return riskAlertRepository.findByStatusOrderByCreatedAtDesc(AlertStatus.OPEN, pageable)
                .map(RiskAlertResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<RiskAlertResponse> getAlertsByUser(UUID userId, Pageable pageable) {
        return riskAlertRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(RiskAlertResponse::from);
    }

    @Transactional(readOnly = true)
    public long countOpenAlerts() {
        return riskAlertRepository.countByStatus(AlertStatus.OPEN);
    }

    @AuditLog(action = AuditAction.ALERT_REVIEWED, entityType = "RiskAlert")
    @Transactional
    public RiskAlertResponse reviewAlert(UUID alertId, AlertReviewRequest request, UUID adminId) {
        RiskAlert alert = riskAlertRepository.findById(alertId)
                .orElseThrow(() -> new AppException("Alert not found: " + alertId, HttpStatus.NOT_FOUND));
        alert.setStatus(request.status());
        alert.setReviewedByAdminId(adminId);
        alert.setAdminNotes(request.notes());
        alert.setReviewedAt(LocalDateTime.now());
        return RiskAlertResponse.from(riskAlertRepository.save(alert));
    }

    private String generateAlertNumber() {
        return "ALT" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }
}
