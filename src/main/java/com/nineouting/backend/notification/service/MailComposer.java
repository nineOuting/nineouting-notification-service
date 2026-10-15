package com.nineouting.backend.notification.service;

import com.nineouting.backend.notification.config.MailDeliveryProperties;
import com.nineouting.backend.notification.domain.OutgoingMail;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 메일 종류별 제목·본문 (일반 텍스트). 값은 기록할 때 넣어 둔 params(JSON)에서 꺼낸다.
 */
@Component
@RequiredArgsConstructor
public class MailComposer {

    private static final String PREFIX = "[AI 회의 어시스턴트] ";
    private static final TypeReference<Map<String, Object>> PARAMS_TYPE = new TypeReference<>() { };

    private final JsonMapper jsonMapper;
    private final MailDeliveryProperties properties;

    public SimpleMailMessage compose(OutgoingMail mail) {
        Map<String, Object> params = jsonMapper.readValue(mail.getParams(), PARAMS_TYPE);
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.from());
        message.setTo(mail.getToEmail());
        switch (mail.getKind()) {
            case EMAIL_VERIFICATION -> {
                message.setSubject(PREFIX + "이메일 인증 코드");
                message.setText("""
                        회원가입 화면에 아래 인증 코드를 입력해 주세요.

                        인증 코드: %s

                        코드는 10분 동안 쓸 수 있습니다. 직접 요청하지 않았다면 이 메일을 무시해 주세요.
                        """.formatted(params.get("code")));
            }
            case PASSWORD_RESET -> {
                message.setSubject(PREFIX + "비밀번호 재설정");
                message.setText("""
                        아래 링크에서 새 비밀번호를 설정해 주세요.

                        %s

                        링크는 30분 동안 쓸 수 있습니다. 직접 요청하지 않았다면 이 메일을 무시해 주세요.
                        """.formatted(params.get("resetLink")));
            }
            case MINUTES_CONFIRMED -> {
                message.setSubject(PREFIX + "'%s' 회의록이 확정되었습니다".formatted(params.get("title")));
                message.setText("""
                        참석한 회의의 회의록이 확정되었습니다.

                        회의록: %s
                        바로가기: %s/minutes/%s
                        """.formatted(params.get("title"), properties.frontendBaseUrl(), params.get("minutesId")));
            }
        }
        return message;
    }
}
