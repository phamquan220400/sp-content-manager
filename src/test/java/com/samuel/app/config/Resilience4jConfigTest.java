package com.samuel.app.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class Resilience4jConfigTest {

    @Mock
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Mock
    private CircuitBreaker youtubeCircuitBreaker;

    @Mock
    private CircuitBreaker tiktokCircuitBreaker;

    private Resilience4jConfig resilience4jConfig;

    @BeforeEach
    void setUp() {
        resilience4jConfig = new Resilience4jConfig(circuitBreakerRegistry);
    }

    @Test
    void should_createConfig_when_constructorCalled_then_registryInjected() {
        // When
        Resilience4jConfig config = new Resilience4jConfig(circuitBreakerRegistry);

        // Then
        assertNotNull(config);
        // Config should be created without exceptions
    }

    @Test
    void should_registerEventListeners_when_postConstructCalled_then_listenersRegisteredForAllCircuitBreakers() {
        // Given
        when(youtubeCircuitBreaker.getEventPublisher()).thenReturn(mock(CircuitBreaker.EventPublisher.class));
        when(tiktokCircuitBreaker.getEventPublisher()).thenReturn(mock(CircuitBreaker.EventPublisher.class));
        
        Set<CircuitBreaker> circuitBreakers = Set.of(youtubeCircuitBreaker, tiktokCircuitBreaker);
        when(circuitBreakerRegistry.getAllCircuitBreakers()).thenReturn(circuitBreakers);

        // When
        resilience4jConfig.registerEventListeners();

        // Then
        verify(circuitBreakerRegistry).getAllCircuitBreakers();
        verify(youtubeCircuitBreaker).getEventPublisher();
        verify(tiktokCircuitBreaker).getEventPublisher();
    }

    @Test
    void should_handleEmptyRegistry_when_noCircuitBreakersExist_then_noExceptionThrown() {
        // Given
        when(circuitBreakerRegistry.getAllCircuitBreakers()).thenReturn(Set.of());

        // When & Then - should not throw exception
        assertDoesNotThrow(() -> resilience4jConfig.registerEventListeners());
        
        verify(circuitBreakerRegistry).getAllCircuitBreakers();
    }

    @Test
    void should_requireCircuitBreakerRegistry_when_constructorCalled_then_registryMustNotBeNull() {
        // When & Then - constructor with null should be handled gracefully
        // Note: In real Spring context, this would be handled by dependency injection
        assertThrows(NullPointerException.class, () -> {
            Resilience4jConfig config = new Resilience4jConfig(null);
            config.registerEventListeners(); // This will fail when accessing null registry
        });
    }

    @Test
    void should_invokeBeanInitialization_when_springContextLoads_then_postConstructMethodCalled() {
        // Given
        CircuitBreakerRegistry realRegistry = CircuitBreakerRegistry.ofDefaults();
        Resilience4jConfig config = new Resilience4jConfig(realRegistry);

        // When & Then - should not throw exception during initialization
        assertDoesNotThrow(() -> config.registerEventListeners());
    }
}