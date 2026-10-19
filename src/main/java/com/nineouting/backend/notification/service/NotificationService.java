package com.nineouting.backend.notification.service;

import com.nineouting.backend.common.web.error.BusinessException;
import com.nineouting.backend.common.web.error.CommonErrorCode;
import com.nineouting.backend.notification.config.NotificationProperties;
import com.nineouting.backend.notification.domain.Notification;
import com.nineouting.backend.notification.domain.NotificationType;
import com.nineouting.backend.notification.dto.NotificationPageResponse;
import com.nineouting.backend.notification.dto.NotificationResponse;
import com.nineouting.backend.notification.dto.UnreadCountResponse;
import com.nineouting.backend.notification.repository.NotificationRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    static final String EVENT_CONNECTED = "connected";
    static final String EVENT_NOTIFICATION = "notification";
    private static final int MAX_PAGE_SIZE = 50;

    private final NotificationRepository notificationRepository;
    private final SseHub sseHub;
    private final SseFanout sseFanout;
    private final NotificationProperties properties;
    private final Clock clock;

    /**
     * 알림을 저장하고, 트랜잭션이 커밋된 뒤에 접속 중인 사용자에게 보낸다
     * (처리가 실패해 되돌려진 알림을 미리 보내지 않기 위해). 사용자가 다른 인스턴스에 붙어 있을 수 있어
     * Redis로 모든 인스턴스에 나눠 보낸다 (SseFanout).
     */
    @Transactional
    public void notify(Long userId, NotificationType type, String message, Long workspaceId, Long targetId) {
        Notification notification = notificationRepository.save(
                new Notification(userId, type, message, workspaceId, targetId, now()));
        NotificationResponse response = NotificationResponse.from(notification);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sseFanout.publish(userId, EVENT_NOTIFICATION, response);
                }
            });
        } else {
            sseFanout.publish(userId, EVENT_NOTIFICATION, response);
        }
    }

    public SseEmitter connect(Long userId) {
        SseEmitter emitter = sseHub.connect(userId);
        sseHub.send(userId, EVENT_CONNECTED, unreadCount(userId));
        return emitter;
    }

    @Transactional(readOnly = true)
    public NotificationPageResponse list(Long userId, Long cursor, int size) {
        int pageSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        PageRequest page = PageRequest.of(0, pageSize + 1);
        List<Notification> found = cursor == null
                ? notificationRepository.findByUserIdOrderByIdDesc(userId, page)
                : notificationRepository.findByUserIdAndIdLessThanOrderByIdDesc(userId, cursor, page);
        boolean hasNext = found.size() > pageSize;
        List<NotificationResponse> items = found.stream().limit(pageSize).map(NotificationResponse::from).toList();
        return new NotificationPageResponse(items, hasNext ? items.getLast().notificationId() : null);
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse unreadCount(Long userId) {
        return new UnreadCountResponse(notificationRepository.countByUserIdAndReadAtIsNull(userId));
    }

    @Transactional
    public void read(Long userId, Long notificationId) {
        notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND))
                .markRead(now());
    }

    @Transactional
    public void readAll(Long userId) {
        notificationRepository.markAllRead(userId, now());
    }

    /** 보관 기간(90일)이 지난 알림을 지운다. */
    @Scheduled(cron = "${nineouting.notification.cleanup-cron:0 0 5 * * *}")
    @Transactional
    public void deleteOld() {
        int deleted = notificationRepository.deleteCreatedBefore(now().minus(properties.retention()));
        if (deleted > 0) {
            log.info("{} 지난 알림 {}건을 지웠습니다", properties.retention(), deleted);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
