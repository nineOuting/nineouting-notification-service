-- 앱 안 알림. 90일 지나면 지운다
CREATE TABLE notifications
(
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    user_id      BIGINT       NOT NULL,
    type         VARCHAR(50)  NOT NULL,
    message      VARCHAR(300) NOT NULL,
    workspace_id BIGINT       NULL,
    target_id    BIGINT       NULL,
    read_at      DATETIME(6)  NULL,
    created_at   DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_notifications_user (user_id, id),
    INDEX idx_notifications_created (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 메일 발송 요청. 실제 발송은 추후 개발 (docs/backlog.md "메일 실제 발송")
CREATE TABLE outgoing_mails
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    to_email   VARCHAR(255) NOT NULL,
    kind       VARCHAR(50)  NOT NULL,
    params     TEXT         NOT NULL,
    status     VARCHAR(20)  NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_outgoing_mails_status (status, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
