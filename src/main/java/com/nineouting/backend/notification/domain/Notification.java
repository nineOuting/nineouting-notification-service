package com.nineouting.backend.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 앱 안 알림. 화면 주소는 넣지 않고, 프론트가 type·workspaceId·targetId로 이동할 화면을 정한다.
 */
@Getter
@Entity
@Table(name = "notifications")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private NotificationType type;

    @Column(nullable = false, length = 300)
    private String message;

    @Column(name = "workspace_id")
    private Long workspaceId;

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Notification(Long userId, NotificationType type, String message, Long workspaceId, Long targetId,
                        LocalDateTime createdAt) {
        this.userId = userId;
        this.type = type;
        this.message = message;
        this.workspaceId = workspaceId;
        this.targetId = targetId;
        this.createdAt = createdAt;
    }

    public boolean isRead() {
        return readAt != null;
    }

    public void markRead(LocalDateTime now) {
        if (readAt == null) {
            readAt = now;
        }
    }
}
