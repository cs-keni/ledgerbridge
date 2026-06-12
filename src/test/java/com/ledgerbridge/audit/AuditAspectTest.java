package com.ledgerbridge.audit;

import com.ledgerbridge.audit.model.AuditAction;
import com.ledgerbridge.audit.service.AuditService;
import com.ledgerbridge.common.audit.AuditAspect;
import com.ledgerbridge.common.audit.AuditLog;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Service;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringJUnitConfig
@Import({AuditAspect.class, AuditAspectTest.AuditableTarget.class})
class AuditAspectTest {

    @TestConfiguration
    @EnableAspectJAutoProxy
    static class Config {
        @Bean
        AuditService auditService() {
            return Mockito.mock(AuditService.class);
        }
    }

    @Autowired AuditService auditService;
    @Autowired AuditableTarget target;

    @Test
    void successfulCall_recordsSuccessOutcome() {
        UUID id = UUID.randomUUID();
        target.doWork(id);
        verify(auditService, times(1)).record(
                eq(AuditAction.ALERT_REVIEWED),
                anyString(),
                eq(id),
                isNull(),
                isNull(),
                isNull(),
                eq("SUCCESS")
        );
    }

    @Test
    void failingCall_recordsFailureOutcomeAndRethrows() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> target.doWorkThatFails(id))
                .isInstanceOf(RuntimeException.class);
        verify(auditService, times(1)).record(
                eq(AuditAction.ALERT_REVIEWED),
                anyString(),
                eq(id),
                isNull(),
                isNull(),
                isNull(),
                eq("FAILURE:RuntimeException")
        );
    }

    @Test
    void entityTypeAnnotationAttribute_overridesDefaultClassName() {
        UUID id = UUID.randomUUID();
        target.doWorkWithEntityType(id);
        verify(auditService, times(1)).record(
                eq(AuditAction.ACCOUNT_CLOSED),
                eq("CustomEntity"),
                eq(id),
                isNull(), isNull(), isNull(),
                eq("SUCCESS")
        );
    }

    @Service
    static class AuditableTarget {

        @AuditLog(action = AuditAction.ALERT_REVIEWED)
        public void doWork(UUID alertId) {}

        @AuditLog(action = AuditAction.ALERT_REVIEWED)
        public void doWorkThatFails(UUID alertId) {
            throw new RuntimeException("simulated failure");
        }

        @AuditLog(action = AuditAction.ACCOUNT_CLOSED, entityType = "CustomEntity")
        public void doWorkWithEntityType(UUID entityId) {}
    }
}
