package com.collab.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Prefix for topics that the clients can subscribe to
        config.enableSimpleBroker("/topic");

        // Prefix for messages that the clients send to the server
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Raw WebSocket endpoint (for Java clients like Vaadin)
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*");

        // SockJS fallback endpoint (for browser clients if needed)
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*").withSockJS();
    }
}
