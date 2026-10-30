package com.nineouting.backend.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.nineouting.backend.notification.config.MailDeliveryProperties;
import com.nineouting.backend.notification.domain.MailKind;
import com.nineouting.backend.notification.domain.OutgoingMail;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import tools.jackson.databind.json.JsonMapper;

/** 메일 종류별 제목·본문 (메일 서버 없이) */
class MailComposerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 15, 12, 0);

    private final MailComposer composer = new MailComposer(JsonMapper.builder().build(),
            new MailDeliveryProperties(true, "no-reply@nineouting.test", "https://app.nineouting.test", 20, 10,
                    Duration.ofDays(1)));

    @Test
    void 인증_코드_메일() {
        SimpleMailMessage message = compose(MailKind.EMAIL_VERIFICATION, "{\"code\":\"123456\"}");

        assertThat(message.getFrom()).isEqualTo("no-reply@nineouting.test");
        assertThat(message.getTo()).containsExactly("user@example.com");
        assertThat(message.getSubject()).isEqualTo("[AI 회의 어시스턴트] 이메일 인증 코드");
        assertThat(message.getText()).contains("인증 코드: 123456").contains("10분");
    }

    @Test
    void 비밀번호_찾기_메일은_인증_번호를_넣는다() {
        SimpleMailMessage message = compose(MailKind.PASSWORD_RESET, "{\"code\":\"654321\"}");

        assertThat(message.getSubject()).isEqualTo("[AI 회의 어시스턴트] 비밀번호 찾기 인증 번호");
        assertThat(message.getText()).contains("인증 번호: 654321").contains("5분");
    }

    @Test
    void 회의록_확정_메일은_제목에_회의명을_넣고_프론트_주소로_바로가기를_만든다() {
        SimpleMailMessage message = compose(MailKind.MINUTES_CONFIRMED, "{\"title\":\"주간 회의\",\"minutesId\":41}");

        assertThat(message.getSubject()).isEqualTo("[AI 회의 어시스턴트] '주간 회의' 회의록이 확정되었습니다");
        assertThat(message.getText()).contains("바로가기: https://app.nineouting.test/minutes/41");
    }

    private SimpleMailMessage compose(MailKind kind, String params) {
        return composer.compose(new OutgoingMail("user@example.com", kind, params, NOW));
    }
}
