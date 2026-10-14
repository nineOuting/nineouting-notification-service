package com.nineouting.backend.notification.service;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 접속 중인 사용자의 SSE 연결을 들고 있다가 새 알림을 보낸다. 한 사용자가 여러 탭·기기로 접속할 수 있다.
 * 이 인스턴스에 연결된 사용자에게만 보낸다 — 여러 인스턴스로 늘릴 때는 docs/backlog.md "SSE 여러 인스턴스".
 */
@Slf4j
@Component
public class SseHub {

    /** 연결이 끊기면 프론트가 다시 연결한다 */
    static final Duration CONNECTION_TIMEOUT = Duration.ofMinutes(30);

    private final Map<Long, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter connect(Long userId) {
        SseEmitter emitter = new SseEmitter(CONNECTION_TIMEOUT.toMillis());
        emitters.computeIfAbsent(userId, id -> ConcurrentHashMap.newKeySet()).add(emitter);
        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> remove(userId, emitter));
        emitter.onError(error -> remove(userId, emitter));
        return emitter;
    }

    public void send(Long userId, String eventName, Object data) {
        for (SseEmitter emitter : emitters.getOrDefault(userId, Set.of())) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data, MediaType.APPLICATION_JSON));
            } catch (IOException | IllegalStateException e) {
                remove(userId, emitter);
            }
        }
    }

    /** 프록시·로드밸런서가 조용한 연결을 끊지 않도록 주석 한 줄을 보낸다. */
    @Scheduled(fixedRateString = "${nineouting.notification.heartbeat-interval:30s}")
    public void heartbeat() {
        emitters.forEach((userId, userEmitters) -> userEmitters.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event().comment("heartbeat"));
            } catch (IOException | IllegalStateException e) {
                remove(userId, emitter);
            }
        }));
    }

    int connectionCount(Long userId) {
        return emitters.getOrDefault(userId, Set.of()).size();
    }

    private void remove(Long userId, SseEmitter emitter) {
        emitters.computeIfPresent(userId, (id, set) -> {
            set.remove(emitter);
            return set.isEmpty() ? null : set;
        });
    }
}
