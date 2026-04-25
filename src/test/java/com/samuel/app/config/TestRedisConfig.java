package com.samuel.app.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test configuration to mock Redis dependencies for integration testing.
 * Uses a ConcurrentHashMap-backed mock so set/get/delete are consistent
 * within the same test — allowing token storage and retrieval to work correctly.
 */
@TestConfiguration
public class TestRedisConfig {

    @Bean
    @Primary
    public StringRedisTemplate stringRedisTemplate() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        ConcurrentHashMap<String, String> store = new ConcurrentHashMap<>();

        when(template.opsForValue()).thenReturn(valueOps);

        doAnswer(inv -> { store.put(inv.getArgument(0), inv.getArgument(1)); return null; })
                .when(valueOps).set(anyString(), anyString(), any(Duration.class));

        when(valueOps.get(anyString()))
                .thenAnswer(inv -> store.get(inv.getArgument(0)));

        when(template.delete(anyString()))
                .thenAnswer(inv -> { store.remove(inv.getArgument(0)); return true; });

        when(template.expire(anyString(), any(Duration.class))).thenReturn(true);

        return template;
    }
}