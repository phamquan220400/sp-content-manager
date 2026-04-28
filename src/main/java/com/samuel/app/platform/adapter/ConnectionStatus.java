package com.samuel.app.platform.adapter;

/**
 * Enum representing the various states a platform connection can be in.
 * Used by platform adapters to report current connection status.
 */
public enum ConnectionStatus {
    CONNECTED,
    DISCONNECTED,
    CIRCUIT_OPEN,
    API_ERROR,
    RATE_LIMITED
}