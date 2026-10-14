package com.nineouting.backend.notification.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param userServiceUrl 알림 문구에 넣을 사용자 이름 조회 (/internal/users)
 * @param retention      알림 보관 기간 (설계: 90일)
 */
@ConfigurationProperties(prefix = "nineouting.notification")
public record NotificationProperties(String userServiceUrl, Duration retention) {
}
