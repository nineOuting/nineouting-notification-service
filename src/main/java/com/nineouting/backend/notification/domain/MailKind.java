package com.nineouting.backend.notification.domain;

public enum MailKind {
    /** 회원가입 인증 코드 (REQ-011) */
    EMAIL_VERIFICATION,
    /** 비밀번호 재설정 링크 (REQ-009) */
    PASSWORD_RESET,
    /** 회의록 확정 공유 (REQ-100) */
    MINUTES_CONFIRMED
}
