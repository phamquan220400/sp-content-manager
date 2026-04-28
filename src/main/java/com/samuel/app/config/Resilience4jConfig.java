package com.samuel.app.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * Resilience4j configuration for circuit breakers, rate limiters, and retry patterns.
 * Registers event listeners for monitoring circuit breaker state transitions.
 */
@Configuration
public class Resilience4jConfig {

    private static final Logger log = LoggerFactory.getLogger(Resilience4jConfig.class);

    private final CircuitBreakerRegistry circuitBreakerRegistry;

    public Resilience4jConfig(CircuitBreakerRegistry circuitBreakerRegistry) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
    }

    @PostConstruct
    public void registerEventListeners() {
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(cb ->
            cb.getEventPublisher().onStateTransition(event ->
                log.info("CircuitBreaker '{}' state transition: {} → {}",
                    event.getCircuitBreakerName(),
                    event.getStateTransition().getFromState(),
                    event.getStateTransition().getToState()
                )
            )
        );
    }
}