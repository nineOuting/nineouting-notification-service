package com.nineouting.backend.notification.config;

import com.nineouting.backend.notification.service.SseFanout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class RedisFanoutConfig {

    /** 모든 인스턴스가 SSE 알림 채널을 구독한다 */
    @Bean
    RedisMessageListenerContainer sseFanoutListener(RedisConnectionFactory connectionFactory, SseFanout fanout) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(fanout, new ChannelTopic(SseFanout.CHANNEL));
        return container;
    }
}
