package com.nineouting.backend.notification;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** 테스트용: Mailpit API로 실제로 받은 메일을 읽는다 */
@Component
public class MailpitClient {

    private final RestClient restClient;

    public MailpitClient(@Value("${test.mailpit.api}") String apiBaseUrl) {
        this.restClient = RestClient.builder().baseUrl(apiBaseUrl).build();
    }

    /** 받는 사람이 email인 메일들의 본문 */
    public List<String> textsTo(String email) {
        String search = restClient.get().uri(uri -> uri.path("/api/v1/search").queryParam("query", "to:" + email).build())
                .retrieve().body(String.class);
        List<String> ids = JsonPath.read(search, "$.messages[*].ID");
        return ids.stream()
                .map(id -> JsonPath.<String>read(restClient.get().uri("/api/v1/message/{id}", id).retrieve().body(String.class), "$.Text"))
                .toList();
    }

    /** 받는 사람이 email인 메일들의 제목 */
    public List<String> subjectsTo(String email) {
        String search = restClient.get().uri(uri -> uri.path("/api/v1/search").queryParam("query", "to:" + email).build())
                .retrieve().body(String.class);
        return JsonPath.read(search, "$.messages[*].Subject");
    }
}
