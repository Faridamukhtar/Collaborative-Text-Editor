package com.collab.backend.config;

import com.collab.backend.server.WebSocketServer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final WebSocketServer webSocketHandler;

    @Autowired
    public WebSocketConfig(WebSocketServer webSocketHandler) {
        this.webSocketHandler = webSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry
            .addHandler(webSocketHandler, "/collaborate")
            .setAllowedOrigins("*"); // Allow connections from anywhere
            
        System.out.println("✅ WebSocket handler registered at /collaborate");

    }
}
