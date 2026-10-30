-- 메일 실제 발송: 재시도 횟수·다음 시도 시각·마지막 오류·보낸 시각
ALTER TABLE outgoing_mails
    ADD COLUMN attempts        INT          NOT NULL DEFAULT 0,
    ADD COLUMN next_attempt_at TIMESTAMP(6) NULL,
    ADD COLUMN last_error      VARCHAR(500) NULL,
    ADD COLUMN sent_at         TIMESTAMP(6) NULL;

-- 이미 쌓여 있던 메일은 바로 보낸다
UPDATE outgoing_mails SET next_attempt_at = created_at WHERE next_attempt_at IS NULL;
ALTER TABLE outgoing_mails ALTER COLUMN next_attempt_at SET NOT NULL;

DROP INDEX idx_outgoing_mails_status;
CREATE INDEX idx_outgoing_mails_due ON outgoing_mails (status, next_attempt_at);
