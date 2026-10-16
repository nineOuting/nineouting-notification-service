package com.nineouting.backend.notification;

import com.nineouting.backend.common.security.jwt.JwksKeyLocator;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Jwks;
import java.security.KeyPair;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 테스트용 PostgreSQL·Kafka·Mailpit(메일 서버), user-service 대신 토큰을 서명할 키.
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

    @Bean
    @ServiceConnection
    KafkaContainer kafkaContainer() {
        return new KafkaContainer(DockerImageName.parse("apache/kafka:3.9.1"));
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
