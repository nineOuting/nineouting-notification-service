package com.nineouting.backend.notification.handler;

import com.nineouting.backend.common.event.workspace.MemberJoinApproved;
import com.nineouting.backend.common.inbox.InboxHandler;
import com.nineouting.backend.common.inbox.InboxMessage;
import com.nineouting.backend.notification.domain.NotificationType;
import com.nineouting.backend.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** REQ-035: 요청자에게 참여 승인 알림 */
@Component
@RequiredArgsConstructor
public class MemberJoinApprovedHandler implements InboxHandler {

    private final JsonMapper jsonMapper;
    private final NotificationService notificationService;

    @Override
    public boolean supports(String eventType) {
        return MemberJoinApproved.TYPE.equals(eventType);
    }

    @Override
    public void handle(InboxMessage message) {
        MemberJoinApproved event = message.payload(jsonMapper, MemberJoinApproved.class);
        notificationService.notify(event.userId(), NotificationType.MEMBER_JOIN_APPROVED,
                "'%s' 워크스페이스 참여가 승인되었습니다".formatted(event.workspaceName()), event.workspaceId(), null);
    }
}
