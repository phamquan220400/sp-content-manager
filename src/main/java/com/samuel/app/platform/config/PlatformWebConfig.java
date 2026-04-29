package com.samuel.app.platform.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Platform web configuration for external API integration.
 * Configures RestTemplate with timeouts optimized for platform API calls.
 */
@Configuration
public class PlatformWebConfig {
    
    /**
     * RestTemplate configured for external platform API calls.
     * Connection timeout: 5 seconds (fail fast on unreachable endpoints)
     * Read timeout: 10 seconds (allow time for token exchange and API responses)
     * 
     * @param builder Spring Boot's RestTemplateBuilder with auto-configured message converters
     * @return configured RestTemplate instance
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
    }
}