package com.nineouting.backend.notification.domain;

public enum MailKind {
    /** 회원가입 인증 코드 (REQ-011) */
    EMAIL_VERIFICATION,
    /** 비밀번호 찾기 인증 번호 (REQ-009) */
    PASSWORD_RESET,
    /** 회의록 확정 공유 (REQ-112, 레거시) */
    MINUTES_CONFIRMED,
    /** 이메일로 워크스페이스 초대 (REQ-127) */
    WORKSPACE_INVITED
}
