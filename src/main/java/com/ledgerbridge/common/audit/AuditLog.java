package com.ledgerbridge.common.audit;

import com.ledgerbridge.audit.model.AuditAction;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a service method for automatic audit logging (D11 — renamed from @Audited
 * to avoid collision with Hibernate Envers). The AuditAspect fires on every
 * invocation including failures, capturing an outcome field (D15).
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {
    AuditAction action();
    String entityType() default "";
    /** Zero-based index of the UUID argument that identifies the audited entity. */
    int entityIdArgIndex() default 0;
}
