package com.nineouting.backend.notification.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 사용자 이름·이메일 조회 주소는 client-common 설정(nineouting.clients.user-service-url)을 쓴다.
 *
 * @param retention 알림 보관 기간 (설계: 90일)
 */
@ConfigurationProperties(prefix = "nineouting.notification")
public record NotificationProperties(Duration retention) {
}
