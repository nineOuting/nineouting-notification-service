package com.nineouting.backend.notification.handler;

import com.nineouting.backend.common.event.user.EmailVerificationRequested;
import com.nineouting.backend.common.event.user.PasswordResetRequested;
import com.nineouting.backend.common.inbox.InboxHandler;
import com.nineouting.backend.common.inbox.InboxMessage;
import com.nineouting.backend.notification.domain.MailKind;
import com.nineouting.backend.notification.domain.OutgoingMail;
import com.nineouting.backend.notification.repository.OutgoingMailRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * REQ-009, 011: 메일 발송 요청을 기록한다. 실제 발송은 MailDeliveryJob이 한다.
 */
@Component
@RequiredArgsConstructor
public class MailRequestHandler implements InboxHandler {

    private final JsonMapper jsonMapper;
    private final OutgoingMailRepository outgoingMailRepository;
    private final Clock clock;

    @Override
    public boolean supports(String eventType) {
        return EmailVerificationRequested.TYPE.equals(eventType) || PasswordResetRequested.TYPE.equals(eventType);
    }

    @Override
    public void handle(InboxMessage message) {
        if (EmailVerificationRequested.TYPE.equals(message.eventType())) {
            EmailVerificationRequested event = message.payload(jsonMapper, EmailVerificationRequested.class);
            record(event.email(), MailKind.EMAIL_VERIFICATION, Map.of("code", event.code()));
        } else {
            PasswordResetRequested event = message.payload(jsonMapper, PasswordResetRequested.class);
            record(event.email(), MailKind.PASSWORD_RESET, Map.of("code", event.code()));
        }
    }

    private void record(String email, MailKind kind, Map<String, String> params) {
        outgoingMailRepository.save(new OutgoingMail(email, kind, jsonMapper.writeValueAsString(params),
                LocalDateTime.now(clock)));
    }
}
