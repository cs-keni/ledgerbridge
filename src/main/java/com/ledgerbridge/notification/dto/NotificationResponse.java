package com.ledgerbridge.notification.dto;

import com.ledgerbridge.notification.model.Notification;
import com.ledgerbridge.notification.model.NotificationType;

import java.time.LocalDateTime;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        UUID userId,
        NotificationType type,
        String title,
        String body,
        boolean read,
        LocalDateTime createdAt
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getUserId(),
                n.getType(),
                n.getTitle(),
                n.getBody(),
                n.getReadAt() != null,
                n.getCreatedAt());
    }
}
