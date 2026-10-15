package com.nineouting.backend.notification.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 메일 실제 발송. SMTP 서버 주소는 spring.mail.* (개발: Mailpit, 운영: AWS SES SMTP).
 *
 * @param enabled         false면 쌓기만 하고 보내지 않는다
 * @param from            보내는 사람 주소
 * @param frontendBaseUrl 메일 안 링크(회의록 바로가기)에 쓰는 프론트 주소
 * @param batchSize       한 번에 꺼내 보낼 메일 수
 * @param maxAttempts     이 횟수만큼 실패하면 FAILED로 남긴다
 * @param sentRetention   보낸 메일 기록 보관 기간 (인증 코드가 들어 있어 짧게)
 */
@ConfigurationProperties(prefix = "nineouting.mail")
public record MailDeliveryProperties(boolean enabled, String from, String frontendBaseUrl, int batchSize,
                                     int maxAttempts, Duration sentRetention) {
}
