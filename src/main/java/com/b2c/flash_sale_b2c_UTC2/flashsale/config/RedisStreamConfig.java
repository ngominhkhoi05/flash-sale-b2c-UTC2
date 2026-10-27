package com.b2c.flash_sale_b2c_UTC2.flashsale.config;

import com.b2c.flash_sale_b2c_UTC2.flashsale.service.FlashSaleOrderStreamConsumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.Subscription;

import java.net.InetAddress;
import java.time.Duration;

@Slf4j
@Configuration
@ConditionalOnBean(RedisConnectionFactory.class)
@ConditionalOnProperty(name = "flashsale.reservation.mode", havingValue = "async", matchIfMissing = false)
public class RedisStreamConfig {

    @Value("${flashsale.stream.consumer-threads:4}")
    private int consumerThreads;

    @Bean(initMethod = "start", destroyMethod = "stop")
    public StreamMessageListenerContainer<String, MapRecord<String, String, String>> streamMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            StringRedisTemplate redisTemplate,
            FlashSaleOrderStreamConsumer consumer
    ) {
        String streamKey = FlashSaleOrderStreamConsumer.STREAM_KEY;
        String groupName = FlashSaleOrderStreamConsumer.GROUP_NAME;

        // Tự động khởi tạo Consumer Group nếu chưa tồn tại
        try {
            redisTemplate.opsForStream().createGroup(streamKey, groupName);
            log.info("[REDIS_STREAM] Created consumer group '{}' on stream '{}'", groupName, streamKey);
        } catch (Exception e) {
            // Nhóm đã tồn tại (BUSYGROUP) là bình thường
            log.info("[REDIS_STREAM] Consumer group '{}' already exists or ready: {}", groupName, e.getMessage());
        }

        StreamMessageListenerContainer.StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
                StreamMessageListenerContainer.StreamMessageListenerContainerOptions.builder()
                        .pollTimeout(Duration.ofSeconds(1))
                        .batchSize(10)
                        .build();

        StreamMessageListenerContainer<String, MapRecord<String, String, String>> container =
                StreamMessageListenerContainer.create(connectionFactory, options);

        String consumerName = "worker-" + getHostName();

        container.receive(
                Consumer.from(groupName, consumerName),
                StreamOffset.create(streamKey, ReadOffset.lastConsumed()),
                consumer
        );

        log.info("[REDIS_STREAM] Registered consumer '{}' in group '{}' for stream '{}'", consumerName, groupName, streamKey);
        return container;
    }

    private String getHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown-" + System.currentTimeMillis();
        }
    }
}
