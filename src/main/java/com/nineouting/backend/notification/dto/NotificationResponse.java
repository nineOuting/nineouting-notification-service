package com.nineouting.backend.notification.dto;

import com.nineouting.backend.notification.domain.Notification;
import com.nineouting.backend.notification.domain.NotificationType;
import java.time.LocalDateTime;

public record NotificationResponse(Long notificationId, NotificationType type, String message, Long workspaceId,
                                   Long targetId, boolean read, LocalDateTime createdAt) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(notification.getId(), notification.getType(), notification.getMessage(),
                notification.getWorkspaceId(), notification.getTargetId(), notification.isRead(),
                notification.getCreatedAt());
    }
}
