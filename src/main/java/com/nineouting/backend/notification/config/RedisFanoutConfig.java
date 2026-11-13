package com.nineouting.backend.notification.config;

import com.nineouting.backend.notification.service.SseFanout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class RedisFanoutConfig {

    /**
     * 모든 인스턴스가 SSE 알림 채널을 구독한다.
     * 받은 메시지는 스레드 하나에서 받은 순서대로 보낸다. 기본값(메시지마다 새 스레드)은 알림이 만든 순서와
     * 다르게 화면에 갈 수 있다 (SseFanoutOrderTest).
     */
    @Bean
    RedisMessageListenerContainer sseFanoutListener(RedisConnectionFactory connectionFactory, SseFanout fanout) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setTaskExecutor(inOrderExecutor());
        container.addMessageListener(fanout, new ChannelTopic(SseFanout.CHANNEL));
        return container;
    }

    /** 빈으로 두지 않는다 (Executor 빈이 생기면 Spring Boot 기본 작업 실행기가 만들어지지 않는다) */
    private static ThreadPoolTaskExecutor inOrderExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setThreadNamePrefix("sse-fanout-");
        executor.setDaemon(true);
        executor.initialize();
        return executor;
    }
}
