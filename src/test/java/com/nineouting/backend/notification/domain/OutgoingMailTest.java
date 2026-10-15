package com.nineouting.backend.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class OutgoingMailTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 15, 12, 0);

    @Test
    void 실패하면_간격을_늘려_다시_보내고_정한_횟수를_넘기면_FAILED다() {
        OutgoingMail mail = new OutgoingMail("a@example.com", MailKind.EMAIL_VERIFICATION, "{}", NOW);

        mail.markFailed("연결 실패", NOW, 3);
        assertThat(mail.getStatus()).isEqualTo(OutgoingMail.STATUS_PENDING);
        assertThat(mail.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(10));

        mail.markFailed("연결 실패", NOW, 3);
        assertThat(mail.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(20));

        mail.markFailed("x".repeat(600), NOW, 3);
        assertThat(mail.getStatus()).isEqualTo(OutgoingMail.STATUS_FAILED);
        assertThat(mail.getAttempts()).isEqualTo(3);
        assertThat(mail.getLastError()).hasSize(500);
    }

    @Test
    void 재시도_간격은_1시간을_넘지_않는다() {
        OutgoingMail mail = new OutgoingMail("a@example.com", MailKind.EMAIL_VERIFICATION, "{}", NOW);
        for (int i = 0; i < 15; i++) {
            mail.markFailed("연결 실패", NOW, 100);
        }

        assertThat(mail.getNextAttemptAt()).isEqualTo(NOW.plusHours(1));
    }

    @Test
    void 보내면_SENT와_보낸_시각을_남긴다() {
        OutgoingMail mail = new OutgoingMail("a@example.com", MailKind.EMAIL_VERIFICATION, "{}", NOW);
        mail.markFailed("연결 실패", NOW, 10);

        mail.markSent(NOW.plusMinutes(1));

        assertThat(mail.getStatus()).isEqualTo(OutgoingMail.STATUS_SENT);
        assertThat(mail.getSentAt()).isEqualTo(NOW.plusMinutes(1));
        assertThat(mail.getLastError()).isNull();
    }
}
