package com.nineouting.backend.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 보낼 메일. MailDeliveryJob이 PENDING인 메일을 꺼내 보낸다.
 * 실패하면 간격을 늘려 다시 보내고, 정해진 횟수를 넘기면 FAILED로 남긴다 (원인 확인용).
 */
@Getter
@Entity
@Table(name = "outgoing_mails")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutgoingMail {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_FAILED = "FAILED";

    private static final Duration FIRST_RETRY_DELAY = Duration.ofSeconds(10);
    private static final Duration MAX_RETRY_DELAY = Duration.ofHours(1);
    private static final int MAX_ERROR_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "to_email", nullable = false)
    private String toEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private MailKind kind;

    /** 메일 내용에 들어갈 값 (JSON). 예: {"code":"123456"} */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String params;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(name = "last_error", length = MAX_ERROR_LENGTH)
    private String lastError;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public OutgoingMail(String toEmail, MailKind kind, String params, LocalDateTime createdAt) {
        this.toEmail = toEmail;
        this.kind = kind;
        this.params = params;
        this.status = STATUS_PENDING;
        this.createdAt = createdAt;
        this.nextAttemptAt = createdAt;
    }

    public void markSent(LocalDateTime now) {
        this.status = STATUS_SENT;
        this.attempts++;
        this.sentAt = now;
        this.lastError = null;
    }

    /** 10초, 20초, 40초 … 최대 1시간 간격으로 다시 보낸다. maxAttempts번 실패하면 FAILED */
    public void markFailed(String error, LocalDateTime now, int maxAttempts) {
        this.attempts++;
        this.lastError = error == null ? null : error.substring(0, Math.min(error.length(), MAX_ERROR_LENGTH));
        if (attempts >= maxAttempts) {
            this.status = STATUS_FAILED;
            return;
        }
        Duration delay = FIRST_RETRY_DELAY.multipliedBy(1L << Math.min(attempts - 1, 20));
        this.nextAttemptAt = now.plus(delay.compareTo(MAX_RETRY_DELAY) > 0 ? MAX_RETRY_DELAY : delay);
    }
}
