package com.nineouting.backend.notification.handler;

import com.nineouting.backend.common.event.meeting.MeetingCancelled;
import com.nineouting.backend.common.event.meeting.MeetingCreated;
import com.nineouting.backend.common.event.meeting.MeetingUpdated;
import com.nineouting.backend.common.inbox.InboxHandler;
import com.nineouting.backend.common.inbox.InboxMessage;
import com.nineouting.backend.notification.domain.NotificationType;
import com.nineouting.backend.notification.service.NotificationService;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 회의 알림: 초대(REQ-058), 정보 변경(REQ-116), 취소(REQ-113). 회의를 만든 사람에게는 보내지 않는다.
 * 수정으로 새로 추가된 사람은 초대 알림, 나머지 참석자는 변경 알림을 받고, 빠진 사람은 받지 않는다.
 */
@Component
@RequiredArgsConstructor
public class MeetingNotificationHandler implements InboxHandler {

    private static final Set<String> TYPES = Set.of(MeetingCreated.TYPE, MeetingUpdated.TYPE, MeetingCancelled.TYPE);
    /** 회의 시각은 한국 시간으로 온다 */
    private static final DateTimeFormatter START_AT = DateTimeFormatter.ofPattern("M월 d일 HH:mm");

    private final JsonMapper jsonMapper;
    private final NotificationService notificationService;

    @Override
    public boolean supports(String eventType) {
        return TYPES.contains(eventType);
    }

    @Override
    public void handle(InboxMessage message) {
        switch (message.eventType()) {
            case MeetingCreated.TYPE -> {
                MeetingCreated event = message.payload(jsonMapper, MeetingCreated.class);
                notifyAll(event.attendeeUserIds(), event.ownerUserId(), NotificationType.MEETING_INVITED,
                        invited(event.title(), event.startAt()), event.workspaceId(), event.meetingId());
            }
            case MeetingUpdated.TYPE -> {
                MeetingUpdated event = message.payload(jsonMapper, MeetingUpdated.class);
                notifyAll(event.addedUserIds(), event.ownerUserId(), NotificationType.MEETING_INVITED,
                        invited(event.title(), event.startAt()), event.workspaceId(), event.meetingId());
                List<Long> others = event.attendeeUserIds().stream()
                        .filter(userId -> !event.addedUserIds().contains(userId))
                        .toList();
                notifyAll(others, event.ownerUserId(), NotificationType.MEETING_UPDATED,
                        "'%s' 회의 정보가 변경되었습니다 (%s)".formatted(event.title(), START_AT.format(event.startAt())),
                        event.workspaceId(), event.meetingId());
            }
            default -> {
                MeetingCancelled event = message.payload(jsonMapper, MeetingCancelled.class);
                notifyAll(event.attendeeUserIds(), event.ownerUserId(), NotificationType.MEETING_CANCELLED,
                        "'%s' 회의가 취소되었습니다 (%s)".formatted(event.title(), START_AT.format(event.startAt())),
                        event.workspaceId(), event.meetingId());
            }
        }
    }

    private static String invited(String title, LocalDateTime startAt) {
        return "'%s' 회의에 초대되었습니다 (%s)".formatted(title, START_AT.format(startAt));
    }

    private void notifyAll(List<Long> userIds, Long ownerUserId, NotificationType type, String text, Long workspaceId,
                           Long meetingId) {
        userIds.stream()
                .filter(userId -> !userId.equals(ownerUserId))
                .forEach(userId -> notificationService.notify(userId, type, text, workspaceId, meetingId));
    }
}
