-- 앱 안 알림. 90일 지나면 지운다
CREATE TABLE notifications
(
    id           BIGINT       GENERATED ALWAYS AS IDENTITY,
    user_id      BIGINT       NOT NULL,
    type         VARCHAR(50)  NOT NULL,
    message      VARCHAR(300) NOT NULL,
    workspace_id BIGINT       NULL,
    target_id    BIGINT       NULL,
    read_at      TIMESTAMP(6) NULL,
    created_at   TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id)
);
CREATE INDEX idx_notifications_user ON notifications (user_id, id);
CREATE INDEX idx_notifications_created ON notifications (created_at);

-- 메일 발송 요청. 실제 발송은 추후 개발 (docs/backlog.md "메일 실제 발송")
CREATE TABLE outgoing_mails
(
    id         BIGINT       GENERATED ALWAYS AS IDENTITY,
    to_email   VARCHAR(255) NOT NULL,
    kind       VARCHAR(50)  NOT NULL,
    params     TEXT         NOT NULL,
    status     VARCHAR(20)  NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id)
);
CREATE INDEX idx_outgoing_mails_status ON outgoing_mails (status, id);
