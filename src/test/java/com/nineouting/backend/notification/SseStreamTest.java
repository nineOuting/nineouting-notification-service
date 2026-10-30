package com.nineouting.backend.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.nineouting.backend.common.client.UserClient;
import com.nineouting.backend.common.event.EventEnvelope;
import com.nineouting.backend.common.event.workspace.MemberJoinApproved;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;

/**
 * 실제 HTTP로 SSE에 연결해 두고, 이벤트가 오면 알림이 바로 전달되는지 확인한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class SseStreamTest {

    private static final Duration WAIT = Duration.ofSeconds(20);

    @Autowired
    Environment environment;

    @Autowired
    LocalMessaging messaging;

    @Autowired
    JsonMapper jsonMapper;

    @MockitoBean
    UserClient userClient;

    @Test
    void 연결하면_connected를_받고_새_알림이_바로_온다() throws Exception {
        Long user = 9001L;
        List<String> lines = new CopyOnWriteArrayList<>();
        HttpRequest request = HttpRequest.newBuilder(URI.create(
                        "http://localhost:" + environment.getProperty("local.server.port") + "/api/notifications/stream"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestcontainersConfiguration.token(user))
                .header(HttpHeaders.ACCEPT, "text/event-stream")
                .build();
        HttpClient.newHttpClient().sendAsync(request, HttpResponse.BodyHandlers.ofLines())
                .thenAccept(response -> {
                    assertThat(response.statusCode()).isEqualTo(200);
                    try (Stream<String> body = response.body()) {
                        body.forEach(lines::add);
                    }
                });

        awaitLine(lines, "event:connected");

        messaging.publish(MemberJoinApproved.TOPIC, null, jsonMapper.writeValueAsString(EventEnvelope.of(
                MemberJoinApproved.TYPE, "test", null, new MemberJoinApproved(3L, "실시간", user))));

        awaitLine(lines, "event:notification");
        assertThat(lines).anyMatch(line -> line.startsWith("data:") && line.contains("'실시간' 워크스페이스 참여가 승인되었습니다"));
    }

    private static void awaitLine(List<String> lines, String expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + WAIT.toMillis();
        while (System.currentTimeMillis() < deadline) {
            if (lines.stream().anyMatch(line -> line.equals(expected))) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError(expected + " 를 받지 못했습니다. 받은 줄: " + lines);
    }
}
