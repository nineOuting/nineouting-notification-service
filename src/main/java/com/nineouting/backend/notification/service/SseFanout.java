package com.nineouting.backend.notification.service;

import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * SSE 알림을 모든 인스턴스로 나눠 보낸다 (Redis Pub/Sub).
 * 알림을 만든 인스턴스와 사용자가 SSE로 붙어 있는 인스턴스가 다를 수 있어서, Redis 채널에 올리고
 * 모든 인스턴스가 받아 자기에게 붙어 있는 연결로만 보낸다. 받는 쪽이 꺼져 있던 동안의 메시지는 사라지지만,
 * 알림은 DB에 남아 있어 다시 연결하면 목록으로 받는다.
 * 받는 것은 스레드 하나가 순서대로 하고(RedisFanoutConfig), 보내기는 사용자 번호로 나눈 스레드에 넘긴다(StripedSender).
 * 그래서 같은 사용자는 순서가 지켜지고, 한 연결이 막혀도 다른 사용자는 늦어지지 않는다.
 */
@Slf4j
@Component
public class SseFanout implements MessageListener {

    public static final String CHANNEL = "nineouting:notification:sse";

    private final StringRedisTemplate redis;
    private final JsonMapper jsonMapper;
    private final SseHub sseHub;
    private final StripedSender sender;

    public SseFanout(StringRedisTemplate redis, JsonMapper jsonMapper, SseHub sseHub, StripedSender sender) {
        this.redis = redis;
        this.jsonMapper = jsonMapper;
        this.sseHub = sseHub;
        this.sender = sender;
    }

    public void publish(Long userId, String eventName, Object data) {
        String message = jsonMapper.writeValueAsString(new Envelope(userId, eventName,
                jsonMapper.writeValueAsString(data)));
        redis.convertAndSend(CHANNEL, message);
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            JsonNode envelope = jsonMapper.readTree(new String(message.getBody(), StandardCharsets.UTF_8));
            long userId = envelope.path("userId").asLong();
            String event = envelope.path("event").asString();
            String data = envelope.path("data").asString();
            sender.execute(userId, () -> sseHub.sendJson(userId, event, data));
        } catch (JacksonException e) {
            log.warn("SSE 알림 메시지 형식이 아니어서 버립니다: {}", e.getMessage());
        }
    }

    /** data는 이미 JSON으로 만든 문자열 */
    record Envelope(Long userId, String event, String data) {
    }
}
