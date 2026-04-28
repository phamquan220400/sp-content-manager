package com.samuel.app.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.Registry;
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
    private Registry.EventPublisher<CircuitBreaker> registryEventPublisher;

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
    }

    @Test
    void should_registerEventListeners_when_postConstructCalled_then_listenersRegisteredForAllCircuitBreakers() {
        // Given
        when(youtubeCircuitBreaker.getEventPublisher()).thenReturn(mock(CircuitBreaker.EventPublisher.class));
        when(tiktokCircuitBreaker.getEventPublisher()).thenReturn(mock(CircuitBreaker.EventPublisher.class));
        when(circuitBreakerRegistry.getAllCircuitBreakers()).thenReturn(Set.of(youtubeCircuitBreaker, tiktokCircuitBreaker));
        when(circuitBreakerRegistry.getEventPublisher()).thenReturn(registryEventPublisher);

        // When
        resilience4jConfig.registerEventListeners();

        // Then
        verify(circuitBreakerRegistry).getAllCircuitBreakers();
        verify(youtubeCircuitBreaker).getEventPublisher();
        verify(tiktokCircuitBreaker).getEventPublisher();
        verify(circuitBreakerRegistry).getEventPublisher();
        verify(registryEventPublisher).onEntryAdded(any());
    }

    @Test
    void should_registerOnEntryAdded_when_noCircuitBreakersExistAtStartup_then_futureCircuitBreakersAreCovered() {
        // Given - empty registry at startup (CBs are created lazily in Resilience4j)
        when(circuitBreakerRegistry.getAllCircuitBreakers()).thenReturn(Set.of());
        when(circuitBreakerRegistry.getEventPublisher()).thenReturn(registryEventPublisher);

        // When
        resilience4jConfig.registerEventListeners();

        // Then - onEntryAdded must be registered so lazily-created CBs get listeners
        verify(registryEventPublisher).onEntryAdded(any());
    }

    @Test
    void should_handleEmptyRegistry_when_noCircuitBreakersExist_then_noExceptionThrown() {
        // Given
        when(circuitBreakerRegistry.getAllCircuitBreakers()).thenReturn(Set.of());
        when(circuitBreakerRegistry.getEventPublisher()).thenReturn(registryEventPublisher);

        // When & Then
        assertDoesNotThrow(() -> resilience4jConfig.registerEventListeners());
    }

    @Test
    void should_requireCircuitBreakerRegistry_when_constructorCalled_then_registryMustNotBeNull() {
        // When & Then - NPE thrown when null registry is used
        assertThrows(NullPointerException.class, () -> {
            Resilience4jConfig config = new Resilience4jConfig(null);
            config.registerEventListeners();
        });
    }

    @Test
    void should_invokeBeanInitialization_when_springContextLoads_then_postConstructMethodCalled() {
        // Given - use a real registry to verify no exception during initialization
        CircuitBreakerRegistry realRegistry = CircuitBreakerRegistry.ofDefaults();
        Resilience4jConfig config = new Resilience4jConfig(realRegistry);

        // When & Then
        assertDoesNotThrow(() -> config.registerEventListeners());
    }
}
