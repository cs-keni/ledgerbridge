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
    public ResponseEntity<Page<AuditLogResponse>> getByEntity(
            @RequestParam String entityType,
            @RequestParam UUID entityId,
            @PageableDefault(size = 20, sort = "occurredAt") Pageable pageable) {
        return ResponseEntity.ok(auditService.getByEntity(entityType, entityId, pageable));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<Page<AuditLogResponse>> getByUser(
            @PathVariable UUID userId,
            @PageableDefault(size = 20, sort = "occurredAt") Pageable pageable) {
        return ResponseEntity.ok(auditService.getByUser(userId, pageable));
    }
}
