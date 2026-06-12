package com.ledgerbridge.risk.controller;

import com.ledgerbridge.auth.model.UserPrincipal;
import com.ledgerbridge.risk.dto.AlertDetailResponse;
import com.ledgerbridge.risk.dto.AlertReviewRequest;
import com.ledgerbridge.risk.dto.AlertStatsResponse;
import com.ledgerbridge.risk.dto.RiskAlertResponse;
import com.ledgerbridge.risk.service.AlertService;
import com.ledgerbridge.risk.service.SseAlertService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;
    private final SseAlertService sseAlertService;

    @GetMapping
    public ResponseEntity<Page<RiskAlertResponse>> list(
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable) {
        if (status != null && !status.isBlank()) {
            return ResponseEntity.ok(alertService.getAlertsByStatus(status, pageable));
        }
        return ResponseEntity.ok(alertService.getAlerts(pageable));
    }

    @GetMapping("/stats")
    public ResponseEntity<AlertStatsResponse> stats() {
        return ResponseEntity.ok(alertService.getAlertStats());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AlertDetailResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(alertService.getAlertById(id));
    }

    @PatchMapping("/{id}/review")
    public ResponseEntity<RiskAlertResponse> review(
            @PathVariable UUID id,
            @Valid @RequestBody AlertReviewRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID adminId = principal.user().getId();
        return ResponseEntity.ok(alertService.reviewAlert(id, request, adminId));
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return sseAlertService.subscribe();
    }
}
