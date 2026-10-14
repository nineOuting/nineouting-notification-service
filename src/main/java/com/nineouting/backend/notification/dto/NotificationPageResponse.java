package com.nineouting.backend.notification.dto;

import java.util.List;

/**
 * @param nextCursor 다음 페이지를 받을 때 cursor로 보낸다. 마지막 페이지면 null
 */
public record NotificationPageResponse(List<NotificationResponse> items, Long nextCursor) {
}
