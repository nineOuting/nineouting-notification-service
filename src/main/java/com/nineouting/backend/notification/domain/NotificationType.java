package com.nineouting.backend.notification.domain;

/** docs/services/notification-service.md 5장 */
public enum NotificationType {
    /** 관리자에게: 참여 요청 (REQ-016). targetId = 참여 요청 ID */
    MEMBER_JOIN_REQUESTED,
    /** 요청자에게: 참여 승인 (REQ-034) */
    MEMBER_JOIN_APPROVED,
    /** 참석자에게: 회의 초대 (REQ-058). 수정으로 새로 추가된 사람도 받는다. targetId = 회의 ID */
    MEETING_INVITED,
    /** 참석자에게: 회의 정보 변경 (REQ-115). targetId = 회의 ID */
    MEETING_UPDATED,
    /** 참석자에게: 회의 취소 (REQ-112). targetId = 회의 ID */
    MEETING_CANCELLED
}
