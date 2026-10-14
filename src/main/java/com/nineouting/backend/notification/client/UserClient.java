package com.nineouting.backend.notification.client;

import com.nineouting.backend.common.web.response.ApiResponse;
import com.nineouting.backend.notification.config.NotificationProperties;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 알림 문구에 넣을 사용자 이름을 user-service에서 조회한다.
 * 실패하면 예외를 던진다 — inbox가 나중에 다시 처리한다.
 */
@Component
public class UserClient {

    /** user-service가 한 번에 받는 최대 개수 */
    private static final int BATCH_SIZE = 100;
    private static final ParameterizedTypeReference<ApiResponse<List<UserSummary>>> RESPONSE_TYPE =
            new ParameterizedTypeReference<>() { };

    private final RestClient restClient;

    public UserClient(RestClient.Builder builder, NotificationProperties properties) {
        this.restClient = builder.baseUrl(properties.userServiceUrl()).build();
    }

    /** 없는 사용자는 결과에서 빠진다. */
    public Map<Long, UserSummary> findUsers(Collection<Long> userIds) {
        List<Long> ids = userIds.stream().distinct().toList();
        Map<Long, UserSummary> result = new HashMap<>();
        for (int from = 0; from < ids.size(); from += BATCH_SIZE) {
            fetch(ids.subList(from, Math.min(from + BATCH_SIZE, ids.size())))
                    .forEach(user -> result.put(user.userId(), user));
        }
        return result;
    }

    private List<UserSummary> fetch(List<Long> ids) {
        try {
            ApiResponse<List<UserSummary>> response = restClient.get()
                    .uri(uri -> uri.path("/internal/users")
                            .queryParam("ids", ids.stream().map(String::valueOf).collect(Collectors.joining(",")))
                            .build())
                    .retrieve()
                    .body(RESPONSE_TYPE);
            return response == null || response.data() == null ? new ArrayList<>() : response.data();
        } catch (RestClientException e) {
            throw new IllegalStateException("user-service 조회 실패", e);
        }
    }
}
