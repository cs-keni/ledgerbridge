package com.ledgerbridge.audit.controller;

import com.ledgerbridge.audit.dto.AuditLogResponse;
import com.ledgerbridge.audit.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/audit-log")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    @GetMapping
    public ResponseEntity<Page<AuditLogResponse>> list(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) UUID entityId,
            @PageableDefault(size = 20, sort = "occurredAt") Pageable pageable) {
        if (entityType != null && entityId != null) {
            return ResponseEntity.ok(auditService.getByEntity(entityType, entityId, pageable));
        }
        return ResponseEntity.ok(auditService.listAll(pageable));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<Page<AuditLogResponse>> getByUser(
            @PathVariable UUID userId,
            @PageableDefault(size = 20, sort = "occurredAt") Pageable pageable) {
        return ResponseEntity.ok(auditService.getByUser(userId, pageable));
    }
}
