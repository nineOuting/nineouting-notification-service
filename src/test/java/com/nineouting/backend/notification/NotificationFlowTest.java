package com.nineouting.backend.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nineouting.backend.common.client.UserClient;
import com.nineouting.backend.common.client.UserSummary;
import com.nineouting.backend.common.event.EventEnvelope;
import com.nineouting.backend.common.event.meeting.MeetingCancelled;
import com.nineouting.backend.common.event.meeting.MeetingCreated;
import com.nineouting.backend.common.event.meeting.MeetingUpdated;
import com.nineouting.backend.common.event.minutes.MinutesConfirmed;
import com.nineouting.backend.common.event.minutes.MinutesReviewRequested;
import com.nineouting.backend.common.event.user.EmailVerificationRequested;
import com.nineouting.backend.common.event.user.PasswordResetRequested;
import com.nineouting.backend.common.event.workspace.MemberJoinApproved;
import com.nineouting.backend.common.event.workspace.MemberJoinRequested;
import com.nineouting.backend.notification.domain.MailKind;
import com.nineouting.backend.notification.repository.OutgoingMailRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

/**
 * 실제 Kafka 토픽에 이벤트를 넣고 inbox → 처리기 → 알림 API까지 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class NotificationFlowTest {

    private static final Duration WAIT = Duration.ofSeconds(20);
    private static final AtomicLong NEXT_ID = new AtomicLong(5000);

    @Autowired
    MockMvc mockMvc;

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    JsonMapper jsonMapper;

    @Autowired
    OutgoingMailRepository outgoingMailRepository;

    @MockitoBean
    UserClient userClient;

    @BeforeEach
    void fakeUserService() {
        given(userClient.findUsers(anyCollection())).willAnswer(invocation -> {
            java.util.Collection<Long> ids = invocation.getArgument(0);
            return ids.stream().collect(java.util.stream.Collectors.toMap(id -> id,
                    id -> new UserSummary(id, "요청자" + id, "user" + id + "@example.com", null)));
        });
    }

    @Test
    void 참여_요청이_오면_관리자에게_알림이_생기고_읽음_처리할_수_있다() throws Exception {
        Long admin = NEXT_ID.incrementAndGet();
        Long requester = NEXT_ID.incrementAndGet();

        publish(MemberJoinRequested.TOPIC, MemberJoinRequested.TYPE,
                new MemberJoinRequested(10L, "팀 워크스페이스", 77L, requester, admin));

        await(() -> unreadCount(admin) == 1);
        call(admin, get("/api/notifications"))
                .andExpect(jsonPath("$.data.items[0].type").value("MEMBER_JOIN_REQUESTED"))
                .andExpect(jsonPath("$.data.items[0].message").value("요청자" + requester + "님이 '팀 워크스페이스' 워크스페이스 참여를 요청했습니다"))
                .andExpect(jsonPath("$.data.items[0].workspaceId").value(10))
                .andExpect(jsonPath("$.data.items[0].targetId").value(77))
                .andExpect(jsonPath("$.data.items[0].read").value(false));
        Long notificationId = id(call(admin, get("/api/notifications")), "$.data.items[0].notificationId");

        call(requester, patch("/api/notifications/" + notificationId + "/read")).andExpect(status().isNotFound());
        call(admin, patch("/api/notifications/" + notificationId + "/read")).andExpect(status().isOk());
        assertThat(unreadCount(admin)).isZero();
    }

    @Test
    void 같은_이벤트가_두_번_와도_알림은_하나다() throws Exception {
        Long user = NEXT_ID.incrementAndGet();
        String envelope = envelope(MemberJoinApproved.TYPE, new MemberJoinApproved(11L, "스터디", user));

        kafkaTemplate.send(MemberJoinApproved.TOPIC, "11", envelope).get();
        kafkaTemplate.send(MemberJoinApproved.TOPIC, "11", envelope).get();
        // 다른 이벤트가 처리될 때까지 기다리면 앞의 두 메시지도 처리가 끝난 것이다
        publish(MemberJoinApproved.TOPIC, MemberJoinApproved.TYPE, new MemberJoinApproved(12L, "다른 곳", user));

        await(() -> unreadCount(user) == 2);
        call(user, get("/api/notifications"))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[1].message").value("'스터디' 워크스페이스 참여가 승인되었습니다"));
    }

    @Test
    void user_service가_잠시_안되면_나중에_다시_처리한다() throws Exception {
        Long admin = NEXT_ID.incrementAndGet();
        Long requester = NEXT_ID.incrementAndGet();
        given(userClient.findUsers(anyCollection()))
                .willThrow(new IllegalStateException("user-service 조회 실패"))
                .willAnswer(invocation -> Map.of(requester, new UserSummary(requester, "다시됨", null, null)));

        publish(MemberJoinRequested.TOPIC, MemberJoinRequested.TYPE,
                new MemberJoinRequested(20L, "재시도", 1L, requester, admin));

        await(() -> unreadCount(admin) == 1);
        call(admin, get("/api/notifications"))
                .andExpect(jsonPath("$.data.items[0].message").value("다시됨님이 '재시도' 워크스페이스 참여를 요청했습니다"));
    }

    @Test
    void 알림은_최신순으로_나눠_받고_한_번에_모두_읽을_수_있다() throws Exception {
        Long user = NEXT_ID.incrementAndGet();
        for (long workspaceId = 1; workspaceId <= 3; workspaceId++) {
            publish(MemberJoinApproved.TOPIC, MemberJoinApproved.TYPE, new MemberJoinApproved(workspaceId, "W" + workspaceId, user));
        }
        await(() -> unreadCount(user) == 3);

        ResultActions first = call(user, get("/api/notifications").param("size", "2"))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.nextCursor").isNumber());
        Long cursor = id(first, "$.data.nextCursor");
        call(user, get("/api/notifications").param("size", "2").param("cursor", cursor.toString()))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.nextCursor").doesNotExist());

        call(user, patch("/api/notifications/read-all")).andExpect(status().isOk());
        assertThat(unreadCount(user)).isZero();
    }

    @Test
    void 메일_요청은_보낼_메일로_기록한다() throws Exception {
        String email = "mail-" + NEXT_ID.incrementAndGet() + "@example.com";

        publish(EmailVerificationRequested.TOPIC, EmailVerificationRequested.TYPE, new EmailVerificationRequested(email, "123456"));
        publish(PasswordResetRequested.TOPIC, PasswordResetRequested.TYPE, new PasswordResetRequested(email, "http://front/reset?token=t"));

        await(() -> outgoingMailRepository.findAll().stream().filter(mail -> mail.getToEmail().equals(email)).count() == 2);
        assertThat(outgoingMailRepository.findAll()).filteredOn(mail -> mail.getToEmail().equals(email))
                .extracting("kind", "params", "status")
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(MailKind.EMAIL_VERIFICATION, "{\"code\":\"123456\"}", "PENDING"),
                        org.assertj.core.groups.Tuple.tuple(MailKind.PASSWORD_RESET, "{\"resetLink\":\"http://front/reset?token=t\"}", "PENDING"));
    }

    @Test
    void 회의_초대_변경_취소를_만든_사람을_빼고_알린다() throws Exception {
        Long owner = NEXT_ID.incrementAndGet();
        Long stays = NEXT_ID.incrementAndGet();
        Long added = NEXT_ID.incrementAndGet();
        Long removed = NEXT_ID.incrementAndGet();
        LocalDateTime startAt = LocalDateTime.of(2026, 10, 20, 14, 0);

        publish(MeetingCreated.TOPIC, MeetingCreated.TYPE,
                new MeetingCreated(31L, 3L, 4L, "주간 회의", startAt, owner, List.of(owner, stays, removed)));
        publish(MeetingUpdated.TOPIC, MeetingUpdated.TYPE, new MeetingUpdated(31L, 3L, 4L, "주간 회의", startAt, owner,
                List.of(owner, stays, added), List.of(added), List.of(removed)));
        publish(MeetingCancelled.TOPIC, MeetingCancelled.TYPE,
                new MeetingCancelled(31L, 3L, 4L, "주간 회의", startAt, owner, List.of(owner, stays, added)));

        await(() -> unreadCount(stays) == 3 && unreadCount(added) == 2);
        call(stays, get("/api/notifications"))
                .andExpect(jsonPath("$.data.items[*].type").value(containsInAnyOrder("MEETING_CANCELLED", "MEETING_UPDATED", "MEETING_INVITED")))
                .andExpect(jsonPath("$.data.items[?(@.type == 'MEETING_INVITED')].message")
                        .value(contains("'주간 회의' 회의에 초대되었습니다 (10월 20일 14:00)")))
                .andExpect(jsonPath("$.data.items[*].targetId").value(contains(31, 31, 31)));
        call(added, get("/api/notifications"))
                .andExpect(jsonPath("$.data.items[*].type").value(containsInAnyOrder("MEETING_CANCELLED", "MEETING_INVITED")));
        assertThat(unreadCount(removed)).isEqualTo(1);
        assertThat(unreadCount(owner)).isZero();
    }

    @Test
    void 회의록_검토_요청과_확정을_알리고_확정_메일을_기록한다() throws Exception {
        Long writer = NEXT_ID.incrementAndGet();
        Long reviewer = NEXT_ID.incrementAndGet();

        publish(MinutesReviewRequested.TOPIC, MinutesReviewRequested.TYPE,
                new MinutesReviewRequested(41L, 31L, 3L, "주간 회의", writer, List.of(reviewer)));
        publish(MinutesConfirmed.TOPIC, MinutesConfirmed.TYPE,
                new MinutesConfirmed(41L, 31L, 3L, 4L, "주간 회의", List.of(writer, reviewer), LocalDateTime.now()));

        await(() -> unreadCount(reviewer) == 2 && unreadCount(writer) == 1);
        call(reviewer, get("/api/notifications"))
                .andExpect(jsonPath("$.data.items[*].type").value(containsInAnyOrder("MINUTES_REVIEW_REQUESTED", "MINUTES_CONFIRMED")))
                .andExpect(jsonPath("$.data.items[?(@.type == 'MINUTES_CONFIRMED')].message")
                        .value(contains("'주간 회의' 회의록이 확정되었습니다")))
                .andExpect(jsonPath("$.data.items[*].targetId").value(contains(41, 41)));
        assertThat(outgoingMailRepository.findAll())
                .filteredOn(mail -> mail.getKind() == MailKind.MINUTES_CONFIRMED)
                .extracting("toEmail")
                .contains("user" + writer + "@example.com", "user" + reviewer + "@example.com");
    }

    @Test
    void 로그인하지_않으면_알림을_볼_수_없다() throws Exception {
        mockMvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
    }

    private void publish(String topic, String type, Object payload) throws Exception {
        kafkaTemplate.send(topic, envelope(type, payload)).get();
    }

    private String envelope(String type, Object payload) {
        return jsonMapper.writeValueAsString(EventEnvelope.of(type, "test", null, payload));
    }

    private long unreadCount(Long userId) {
        try {
            Number count = JsonPath.read(call(userId, get("/api/notifications/unread-count"))
                    .andReturn().getResponse().getContentAsString(), "$.data.count");
            return count.longValue();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private ResultActions call(Long userId, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + TestcontainersConfiguration.token(userId)));
    }

    private static Long id(ResultActions result, String path) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), path)).longValue();
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        Instant deadline = Instant.now().plus(WAIT);
        while (Instant.now().isBefore(deadline)) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(200);
        }
        throw new AssertionError(WAIT + " 안에 조건을 만족하지 못했습니다");
    }
}
