package com.lootfast.backend.notification.realtime;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
@ConditionalOnProperty(name = "lootfast.realtime.relay", havingValue = "redis", matchIfMissing = true)
class RedisRealtimeConfig {

    @Bean
    RedisMessageListenerContainer realtimeRedisListenerContainer(
            RedisConnectionFactory connectionFactory,
            RedisUserEventGateway gateway,
            RealtimeProperties properties
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(gateway, new ChannelTopic(properties.redisChannel()));
        return container;
    }
}
