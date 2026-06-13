package com.ledgerbridge.risk.metrics;

import com.ledgerbridge.risk.model.AlertSeverity;
import com.ledgerbridge.risk.model.AlertType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * Central registry for risk-engine-specific Prometheus metrics.
 *
 * Metric names (Micrometer → Prometheus):
 *   risk.scoring.duration      → risk_scoring_duration_seconds{alert_triggered}
 *   risk.alerts.created        → risk_alerts_created_total{alert_type, severity}
 *   risk.dlt.messages          → risk_dlt_messages_total
 */
@Component
public class RiskMetrics {

    private final MeterRegistry registry;
    private final Counter dltCounter;

    public RiskMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.dltCounter = Counter.builder("risk.dlt.messages")
                .description("Transaction events that exhausted all Kafka retries and landed on the DLT")
                .register(registry);
    }

    /** Start a timing sample before rule evaluation begins. */
    public Timer.Sample startScoringSample() {
        return Timer.start(registry);
    }

    /**
     * Stop the timing sample after evaluation completes.
     * Tagged with whether an alert was triggered so latency can be compared across outcomes.
     */
    public void stopScoringSample(Timer.Sample sample, boolean alertTriggered) {
        sample.stop(Timer.builder("risk.scoring.duration")
                .description("Wall time to evaluate all 4 risk rules and decide alert threshold")
                .tag("alert_triggered", String.valueOf(alertTriggered))
                .register(registry));
    }

    /** Increment alert creation counter. Called once per alert persisted. */
    public void incrementAlertCreated(AlertType alertType, AlertSeverity severity) {
        Counter.builder("risk.alerts.created")
                .description("Number of risk alerts created, by dominant alert type and severity")
                .tag("alert_type", alertType.name())
                .tag("severity", severity.name())
                .register(registry)
                .increment();
    }

    /** Increment DLT counter. Called from the @DltHandler. */
    public void incrementDlt() {
        dltCounter.increment();
    }
}
