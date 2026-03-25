package com.easypublish.websocket.sync;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP websocket configuration for sync updates.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    // Extracted to properties: app.cors.allowed-origins
    private final String[] allowedOrigins;
    // Extracted to properties: app.websocket.broker-prefix
    private final String brokerPrefix;
    // Extracted to properties: app.websocket.app-prefix
    private final String appPrefix;
    // Extracted to properties: app.websocket.endpoint
    private final String wsEndpoint;

    public WebSocketConfig(
            @Value("${app.cors.allowed-origins:http://localhost:5173}") String[] allowedOrigins,
            @Value("${app.websocket.broker-prefix:/topic}") String brokerPrefix,
            @Value("${app.websocket.app-prefix:/app}") String appPrefix,
            @Value("${app.websocket.endpoint:/ws-sync}") String wsEndpoint
    ) {
        this.allowedOrigins = allowedOrigins;
        this.brokerPrefix = brokerPrefix;
        this.appPrefix = appPrefix;
        this.wsEndpoint = wsEndpoint;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker(brokerPrefix);
        config.setApplicationDestinationPrefixes(appPrefix);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint(wsEndpoint).setAllowedOrigins(allowedOrigins);
    }
}
