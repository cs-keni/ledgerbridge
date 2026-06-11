package com.ledgerbridge.risk.service;

import com.ledgerbridge.risk.dto.RiskAlertResponse;
import com.ledgerbridge.risk.model.AlertSeverity;
import com.ledgerbridge.risk.model.AlertStatus;
import com.ledgerbridge.risk.model.AlertType;
import com.ledgerbridge.risk.model.RiskAlert;
import com.ledgerbridge.risk.repository.RiskAlertRepository;
import com.ledgerbridge.transaction.event.TransactionEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AlertService {

    private final RiskAlertRepository riskAlertRepository;

    @Transactional
    public RiskAlert createAlert(TransactionEvent event, AlertType alertType,
                                 AlertSeverity severity, double score,
                                 Map<String, Object> ruleDetails) {
        RiskAlert alert = new RiskAlert();
        alert.setAlertNumber(generateAlertNumber());
        alert.setTransactionId(event.transactionId());
        alert.setUserId(event.userId());
        alert.setAlertType(alertType);
        alert.setSeverity(severity);
        alert.setRiskScore(score);
        alert.setRuleDetails(ruleDetails);
        return riskAlertRepository.save(alert);
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

    private String generateAlertNumber() {
        return "ALT" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }
}
