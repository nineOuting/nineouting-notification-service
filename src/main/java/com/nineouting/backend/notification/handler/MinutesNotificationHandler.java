package com.nineouting.backend.notification.handler;

import com.nineouting.backend.common.client.UserClient;
import com.nineouting.backend.common.client.UserSummary;
import com.nineouting.backend.common.event.minutes.MinutesConfirmed;
import com.nineouting.backend.common.event.minutes.MinutesReviewRequested;
import com.nineouting.backend.common.inbox.InboxHandler;
import com.nineouting.backend.common.inbox.InboxMessage;
import com.nineouting.backend.notification.domain.MailKind;
import com.nineouting.backend.notification.domain.NotificationType;
import com.nineouting.backend.notification.domain.OutgoingMail;
import com.nineouting.backend.notification.repository.OutgoingMailRepository;
import com.nineouting.backend.notification.service.NotificationService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 회의록 알림.
 * <ul>
 *   <li>검토 요청 (REQ-106): 검토할 참석자에게 알림</li>
 *   <li>확정 (REQ-112): 참석자 전원에게 알림 + 메일 기록 (발송은 MailDeliveryJob)</li>
 * </ul>
 * user-service에 연결할 수 없으면 예외를 던져 inbox가 나중에 다시 처리한다. 탈퇴 등으로 없는 사용자는 메일을 건너뛴다.
 */
@Component
@RequiredArgsConstructor
public class MinutesNotificationHandler implements InboxHandler {

    private final JsonMapper jsonMapper;
    private final NotificationService notificationService;
    private final UserClient userClient;
    private final OutgoingMailRepository outgoingMailRepository;
    private final Clock clock;

    @Override
    public boolean supports(String eventType) {
        return MinutesReviewRequested.TYPE.equals(eventType) || MinutesConfirmed.TYPE.equals(eventType);
    }

    @Override
    public void handle(InboxMessage message) {
        if (MinutesReviewRequested.TYPE.equals(message.eventType())) {
            MinutesReviewRequested event = message.payload(jsonMapper, MinutesReviewRequested.class);
            event.reviewerUserIds().forEach(userId -> notificationService.notify(userId,
                    NotificationType.MINUTES_REVIEW_REQUESTED, "'%s' 회의록 검토를 요청받았습니다".formatted(event.title()),
                    event.workspaceId(), event.minutesId()));
            return;
        }
        MinutesConfirmed event = message.payload(jsonMapper, MinutesConfirmed.class);
        Map<Long, UserSummary> users = userClient.findUsers(event.attendeeUserIds());
        String params = jsonMapper.writeValueAsString(Map.of("title", event.title(), "minutesId", event.minutesId()));
        for (Long userId : event.attendeeUserIds()) {
            notificationService.notify(userId, NotificationType.MINUTES_CONFIRMED,
                    "'%s' 회의록이 확정되었습니다".formatted(event.title()), event.workspaceId(), event.minutesId());
            UserSummary user = users.get(userId);
            if (user != null && user.email() != null) {
                outgoingMailRepository.save(new OutgoingMail(user.email(), MailKind.MINUTES_CONFIRMED, params,
                        LocalDateTime.now(clock)));
            }
        }
    }
}
