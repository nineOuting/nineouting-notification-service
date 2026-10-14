package com.nineouting.backend.notification.handler;

import com.nineouting.backend.common.event.workspace.MemberJoinRequested;
import com.nineouting.backend.common.inbox.InboxHandler;
import com.nineouting.backend.common.inbox.InboxMessage;
import com.nineouting.backend.notification.client.UserClient;
import com.nineouting.backend.notification.client.UserSummary;
import com.nineouting.backend.notification.domain.NotificationType;
import com.nineouting.backend.notification.service.NotificationService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** REQ-016: 관리자에게 참여 요청 알림 */
@Component
@RequiredArgsConstructor
public class MemberJoinRequestedHandler implements InboxHandler {

    /** user-service에서 이름을 찾지 못한 경우 (탈퇴 등) */
    private static final String UNKNOWN_NAME = "알 수 없는 사용자";

    private final JsonMapper jsonMapper;
    private final UserClient userClient;
    private final NotificationService notificationService;

    @Override
    public boolean supports(String eventType) {
        return MemberJoinRequested.TYPE.equals(eventType);
    }

    @Override
    public void handle(InboxMessage message) {
        MemberJoinRequested event = message.payload(jsonMapper, MemberJoinRequested.class);
        UserSummary requester = userClient.findUsers(List.of(event.requesterUserId())).get(event.requesterUserId());
        String name = requester == null ? UNKNOWN_NAME : requester.name();
        notificationService.notify(event.adminUserId(), NotificationType.MEMBER_JOIN_REQUESTED,
                "%s님이 '%s' 워크스페이스 참여를 요청했습니다".formatted(name, event.workspaceName()),
                event.workspaceId(), event.joinRequestId());
    }
}
