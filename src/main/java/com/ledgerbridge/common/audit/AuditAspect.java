package com.ledgerbridge.common.audit;

import com.ledgerbridge.audit.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditService auditService;

    @Around("@annotation(auditLog)")
    public Object around(ProceedingJoinPoint pjp, AuditLog auditLog) throws Throwable {
        String outcome = "SUCCESS";
        try {
            return pjp.proceed();
        } catch (Throwable ex) {
            outcome = "FAILURE:" + ex.getClass().getSimpleName();
            throw ex;
        } finally {
            String entityType = resolveEntityType(auditLog, pjp);
            UUID entityId     = extractEntityId(pjp.getArgs());
            UUID userId       = resolveUserId();
            String ip         = resolveIp();
            String corrId     = MDC.get("correlationId");
            auditService.record(auditLog.action(), entityType, entityId, userId, ip, corrId, outcome);
        }
    }

    private String resolveEntityType(AuditLog annotation, ProceedingJoinPoint pjp) {
        if (!annotation.entityType().isBlank()) return annotation.entityType();
        return pjp.getTarget().getClass().getSimpleName();
    }

    private UUID extractEntityId(Object[] args) {
        if (args == null) return null;
        for (Object arg : args) {
            if (arg instanceof UUID uuid) return uuid;
        }
        return null;
    }

    private UUID resolveUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        try {
            return UUID.fromString(auth.getName());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String resolveIp() {
        try {
            var attrs = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            return attrs.getRequest().getRemoteAddr();
        } catch (IllegalStateException e) {
            // Non-HTTP thread (e.g. Kafka consumer) — IP not available
            return null;
        }
    }
}
