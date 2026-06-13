package com.ledgerbridge.common.demo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refreshes demo timestamps on every startup so alerts and audit entries
 * always appear "recent" in the portfolio demo regardless of when Flyway last ran.
 *
 * Only active when SPRING_PROFILES_ACTIVE=demo.
 */
@Slf4j
@Component
@Profile("demo")
@RequiredArgsConstructor
public class DemoDataRefreshComponent {

    private final JdbcTemplate jdbc;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void refreshTimestamps() {
        log.info("Demo profile: refreshing demo data timestamps");

        // Trigger transactions
        jdbc.update("UPDATE ledger_transaction SET initiated_at = NOW() - INTERVAL '25 minutes', completed_at = NOW() - INTERVAL '24 minutes' WHERE id = 'ee100001-0000-0000-0000-000000000001'");
        jdbc.update("UPDATE ledger_transaction SET initiated_at = NOW() - INTERVAL '1 hour 12 minutes', completed_at = NOW() - INTERVAL '1 hour 11 minutes' WHERE id = 'cc100001-0000-0000-0000-000000000001'");
        jdbc.update("UPDATE ledger_transaction SET initiated_at = NOW() - INTERVAL '3 hours', completed_at = NOW() - INTERVAL '2 hours 59 minutes' WHERE id = 'bb100001-0000-0000-0000-000000000001'");
        jdbc.update("UPDATE ledger_transaction SET initiated_at = NOW() - INTERVAL '5 hours', completed_at = NOW() - INTERVAL '4 hours 59 minutes' WHERE id = 'dd100001-0000-0000-0000-000000000001'");
        jdbc.update("UPDATE ledger_transaction SET initiated_at = NOW() - INTERVAL '18 hours', completed_at = NOW() - INTERVAL '17 hours 59 minutes' WHERE id = 'aa100001-0000-0000-0000-000000000001'");
        jdbc.update("UPDATE ledger_transaction SET initiated_at = NOW() - INTERVAL '2 days', completed_at = NOW() - INTERVAL '2 days' WHERE id = 'aa100002-0000-0000-0000-000000000002'");

        // Risk alerts
        jdbc.update("UPDATE risk_alert SET created_at = NOW() - INTERVAL '25 minutes' WHERE id = 'ee000001-0000-0000-0000-000000000001'");
        jdbc.update("UPDATE risk_alert SET created_at = NOW() - INTERVAL '1 hour 12 minutes' WHERE id = 'cc000001-0000-0000-0000-000000000001'");
        jdbc.update("UPDATE risk_alert SET created_at = NOW() - INTERVAL '3 hours', reviewed_at = NOW() - INTERVAL '2 hours 45 minutes' WHERE id = 'bb000001-0000-0000-0000-000000000001'");
        jdbc.update("UPDATE risk_alert SET created_at = NOW() - INTERVAL '5 hours' WHERE id = 'dd000001-0000-0000-0000-000000000001'");
        jdbc.update("UPDATE risk_alert SET created_at = NOW() - INTERVAL '18 hours', reviewed_at = NOW() - INTERVAL '17 hours' WHERE id = 'aa000001-0000-0000-0000-000000000001'");
        jdbc.update("UPDATE risk_alert SET created_at = NOW() - INTERVAL '2 days', reviewed_at = NOW() - INTERVAL '1 day 22 hours' WHERE id = 'aa000002-0000-0000-0000-000000000002'");

        // Notifications
        jdbc.update("UPDATE notification SET created_at = NOW() - INTERVAL '25 minutes' WHERE id = '0b000001-0000-0000-0000-000000000001'");
        jdbc.update("UPDATE notification SET created_at = NOW() - INTERVAL '1 hour 12 minutes' WHERE id = '0b000002-0000-0000-0000-000000000002'");
        jdbc.update("UPDATE notification SET created_at = NOW() - INTERVAL '17 hours 45 minutes', read_at = NOW() - INTERVAL '16 hours' WHERE id = '0b000003-0000-0000-0000-000000000003'");

        // Audit log (occurred_at, not created_at)
        jdbc.update("UPDATE audit_log SET occurred_at = NOW() - INTERVAL '2 hours 45 minutes' WHERE id = '0a000001-0000-0000-0000-000000000001'");
        jdbc.update("UPDATE audit_log SET occurred_at = NOW() - INTERVAL '17 hours' WHERE id = '0a000002-0000-0000-0000-000000000002'");
        jdbc.update("UPDATE audit_log SET occurred_at = NOW() - INTERVAL '1 day 22 hours' WHERE id = '0a000003-0000-0000-0000-000000000003'");

        log.info("Demo profile: timestamp refresh complete");
    }
}
