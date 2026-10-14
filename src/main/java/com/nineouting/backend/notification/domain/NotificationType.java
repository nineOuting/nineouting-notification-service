package com.nineouting.backend.notification.domain;

/** docs/services/notification-service.md 5장 */
public enum NotificationType {
    /** 관리자에게: 참여 요청 (REQ-016). targetId = 참여 요청 ID */
    MEMBER_JOIN_REQUESTED,
    /** 요청자에게: 참여 승인 (REQ-034) */
    MEMBER_JOIN_APPROVED
}
