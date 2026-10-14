# nineouting-notification-service

다른 서비스의 이벤트를 받아 앱 안 알림(목록·읽음·SSE 실시간 전송)을 만들고, 메일 발송 요청을 기록하는 서비스.

- 설계: [nineouting-docs/docs/services/notification-service.md](https://github.com/nineOuting/nineouting-docs/blob/main/docs/services/notification-service.md)
- 포트: `8085`
- 이벤트는 공통 모듈 `inbox`로 받는다 (DB에 먼저 저장 → 처리, 중복 제거·재시도)
- 메일 실제 발송은 추후 개발 (`outgoing_mails`에 쌓기만 한다)

## 로컬 실행

필요한 것: JDK 21, Docker, 실행 중인 user-service(8081)

1. `~/.gradle/gradle.properties`에 `gpr.user`, `gpr.key`(read:packages 토큰)
2. `docker compose up -d` — MySQL(`localhost:3309`), Kafka(`localhost:9092`)
3. `./gradlew bootRun --args='--spring.profiles.active=local'`

## API

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/notifications?cursor=&size=20` | 알림 목록 (최신순) |
| GET | `/api/notifications/unread-count` | 안 읽은 개수 |
| PATCH | `/api/notifications/{id}/read` | 읽음 |
| PATCH | `/api/notifications/read-all` | 모두 읽음 |
| GET | `/api/notifications/stream` | SSE. `connected`, `notification` 이벤트 |

SSE는 `Authorization` 헤더가 필요하므로 프론트는 fetch 기반 SSE를 쓴다 (기본 `EventSource`는 헤더를 붙일 수 없다).

## 받는 이벤트

| 토픽 | 처리 |
|------|------|
| `workspace.member-join-requested` | 관리자에게 참여 요청 알림 |
| `workspace.member-join-approved` | 요청자에게 승인 알림 |
| `meeting.created` · `meeting.updated` · `meeting.cancelled` | 참석자에게 회의 초대·변경·취소 알림 (만든 사람 제외) |
| `user.email-verification-requested` | 보낼 메일 기록 (인증 코드) |
| `user.password-reset-requested` | 보낼 메일 기록 (재설정 링크) |

## 테스트

```bash
./gradlew test   # Testcontainers로 MySQL·Kafka를 띄워 실제 토픽 → inbox → 알림 → SSE까지 확인한다
```

## 환경 변수

| 이름 | 기본값 | 설명 |
|------|--------|------|
| `SERVER_PORT` | `8085` | |
| `DB_URL` | `jdbc:mysql://localhost:3309/notification_db` | |
| `DB_USERNAME` / `DB_PASSWORD` | `notification_service` | |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | |
| `JWT_JWKS_URI` | `http://localhost:8081/.well-known/jwks.json` | access 토큰 검증 공개키 |
| `USER_SERVICE_URL` | `http://localhost:8081` | 알림 문구의 사용자 이름 조회 |
