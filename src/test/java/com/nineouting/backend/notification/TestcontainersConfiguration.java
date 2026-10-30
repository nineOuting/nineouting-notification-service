package com.nineouting.backend.notification;

import com.nineouting.backend.common.event.meeting.MeetingCancelled;
import com.nineouting.backend.common.event.meeting.MeetingCreated;
import com.nineouting.backend.common.event.meeting.MeetingUpdated;
import com.nineouting.backend.common.event.minutes.MinutesConfirmed;
import com.nineouting.backend.common.event.minutes.MinutesEdited;
import com.nineouting.backend.common.event.minutes.MinutesReviewRequested;
import com.nineouting.backend.common.event.user.EmailVerificationRequested;
import com.nineouting.backend.common.event.user.PasswordResetRequested;
import com.nineouting.backend.common.event.workspace.MemberInvitedByEmail;
import com.nineouting.backend.common.event.workspace.MemberJoinApproved;
import com.nineouting.backend.common.event.workspace.MemberJoinRequested;
import com.nineouting.backend.common.security.jwt.JwksKeyLocator;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Jwks;
import java.security.KeyPair;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.localstack.LocalStackContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * 테스트용 PostgreSQL·LocalStack(SNS·SQS)·Redis·Mailpit(메일 서버), user-service 대신 토큰을 서명할 키.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    private static final KeyPair KEYS = Jwts.SIG.RS256.keyPair().build();
    private static final String KID = "test-key";

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer("postgres:17.6-alpine");
    }

    /** SSE 알림을 인스턴스끼리 나눠 보내는 Pub/Sub */
    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redisContainer() {
        return new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);
    }

    /** 받은 메일을 API(/api/v1/...)로 확인할 수 있는 개발용 메일 서버 */
    @Bean
    GenericContainer<?> mailpitContainer() {
        return new GenericContainer<>(DockerImageName.parse("axllent/mailpit:v1.31.3")).withExposedPorts(1025, 8025);
    }

    @Bean
    DynamicPropertyRegistrar mailpitProperties(GenericContainer<?> mailpitContainer) {
        return registry -> {
            registry.add("spring.mail.host", mailpitContainer::getHost);
            registry.add("spring.mail.port", () -> mailpitContainer.getMappedPort(1025));
            registry.add("test.mailpit.api", () -> "http://" + mailpitContainer.getHost() + ":" + mailpitContainer.getMappedPort(8025));
            registry.add("nineouting.mail.send-interval", () -> "500ms");
        };
    }

    @Bean
    LocalStackContainer localStackContainer() {
        return new LocalStackContainer(DockerImageName.parse("localstack/localstack:4.4")).withServices("sns", "sqs");
    }

    @Bean
    SnsClient snsClient(LocalStackContainer localStack) {
        return SnsClient.builder().endpointOverride(localStack.getEndpoint()).region(Region.of(localStack.getRegion()))
                .credentialsProvider(credentials(localStack)).build();
    }

    @Bean
    SqsClient sqsClient(LocalStackContainer localStack) {
        return SqsClient.builder().endpointOverride(localStack.getEndpoint()).region(Region.of(localStack.getRegion()))
                .credentialsProvider(credentials(localStack)).build();
    }

    @Bean
    LocalMessaging localMessaging(SnsClient snsClient, SqsClient sqsClient) {
        return new LocalMessaging(snsClient, sqsClient);
    }

    /** 이 서비스 큐를 만들어 받는 토픽에 구독시키고 inbox에 큐 주소를 알려 준다 (운영은 Terraform) */
    @Bean
    DynamicPropertyRegistrar messagingProperties(LocalMessaging messaging) {
        return registry -> {
            String queueUrl = messaging.queue("notification-service", List.of(
                    EmailVerificationRequested.TOPIC,
                    PasswordResetRequested.TOPIC,
                    MemberJoinRequested.TOPIC,
                    MemberJoinApproved.TOPIC,
                    MemberInvitedByEmail.TOPIC,
                    MeetingCreated.TOPIC,
                    MeetingUpdated.TOPIC,
                    MeetingCancelled.TOPIC,
                    MinutesReviewRequested.TOPIC,
                    MinutesConfirmed.TOPIC,
                    MinutesEdited.TOPIC));
            registry.add("nineouting.inbox.queue", () -> queueUrl);
        };
    }

    private static StaticCredentialsProvider credentials(LocalStackContainer localStack) {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(localStack.getAccessKey(), localStack.getSecretKey()));
    }

    /** security-common이 user-service JWKS 대신 테스트 공개키로 검증하게 한다. */
    @Bean
    JwksKeyLocator jwksKeyLocator() {
        String jwks = "{\"keys\":[" + Jwks.json(Jwks.builder().key((RSAPublicKey) KEYS.getPublic()).id(KID).build()) + "]}";
        return new JwksKeyLocator(() -> jwks);
    }

    public static String token(Long userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .header().keyId(KID).and()
                .issuer("nineouting-user-service")
                .subject(userId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(1800)))
                .signWith(KEYS.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }
}
