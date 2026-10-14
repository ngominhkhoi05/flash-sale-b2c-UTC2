package com.b2c.flash_sale_b2c_UTC2.common.test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class TestRedisHelper {

    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private RedisConnectionFactory connectionFactory;

    public void flushDb() {
        connectionFactory.getConnection().serverCommands().flushDb();
    }

    public void deleteByPrefix(String prefix) {
        var connection = connectionFactory.getConnection();
        var keys = connection.keyCommands().keys((prefix + "*").getBytes());
        if (keys != null && !keys.isEmpty()) {
            connection.keyCommands().del(keys.toArray(new byte[0][]));
        }
    }
}