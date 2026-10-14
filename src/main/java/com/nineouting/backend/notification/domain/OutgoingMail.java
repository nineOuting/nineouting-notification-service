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
 * 보낼 메일. 실제 발송은 추후 개발이라 지금은 PENDING으로 쌓기만 한다 (docs/backlog.md "메일 실제 발송").
 */
@Getter
@Entity
@Table(name = "outgoing_mails")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutgoingMail {

    public static final String STATUS_PENDING = "PENDING";

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

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public OutgoingMail(String toEmail, MailKind kind, String params, LocalDateTime createdAt) {
        this.toEmail = toEmail;
        this.kind = kind;
        this.params = params;
        this.status = STATUS_PENDING;
        this.createdAt = createdAt;
    }
}
