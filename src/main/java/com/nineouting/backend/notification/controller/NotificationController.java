package com.nineouting.backend.notification.controller;

import com.nineouting.backend.common.security.jwt.AuthenticatedUser;
import com.nineouting.backend.common.security.web.CurrentUser;
import com.nineouting.backend.common.web.response.ApiResponse;
import com.nineouting.backend.notification.dto.NotificationPageResponse;
import com.nineouting.backend.notification.dto.UnreadCountResponse;
import com.nineouting.backend.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private static final String DEFAULT_PAGE_SIZE = "20";

    private final NotificationService notificationService;

    @GetMapping
    public ApiResponse<NotificationPageResponse> list(@CurrentUser AuthenticatedUser user,
                                                      @RequestParam(required = false) Long cursor,
                                                      @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) int size) {
        return ApiResponse.ok(notificationService.list(user.userId(), cursor, size));
    }

    @GetMapping("/unread-count")
    public ApiResponse<UnreadCountResponse> unreadCount(@CurrentUser AuthenticatedUser user) {
        return ApiResponse.ok(notificationService.unreadCount(user.userId()));
    }

    @PatchMapping("/{notificationId}/read")
    public ApiResponse<Void> read(@CurrentUser AuthenticatedUser user, @PathVariable Long notificationId) {
        notificationService.read(user.userId(), notificationId);
        return ApiResponse.ok();
    }

    @PatchMapping("/read-all")
    public ApiResponse<Void> readAll(@CurrentUser AuthenticatedUser user) {
        notificationService.readAll(user.userId());
        return ApiResponse.ok();
    }

    /** SSE. 연결 직후 connected(안 읽은 개수), 새 알림마다 notification 이벤트를 보낸다. */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@CurrentUser AuthenticatedUser user) {
        return notificationService.connect(user.userId());
    }
}
