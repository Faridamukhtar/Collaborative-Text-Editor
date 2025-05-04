package com.collab.backend.config;

import com.collab.backend.websocket.CrdtWebSocketHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;
import org.springframework.context.event.ContextRefreshedEvent;

import java.util.Map;

/**
 * Enhanced WebSocket configuration with debugging
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private static final Logger logger = LoggerFactory.getLogger(WebSocketConfig.class);

    @Value("${server.port:8081}")  // Default to 8081 if not specified
    private int serverPort;

    @Value("${server.servlet.context-path:}")  // Default to empty if not specified
    private String contextPath;

    private final CrdtWebSocketHandler CrdtWebSocketHandler;

    @Autowired
    public WebSocketConfig(CrdtWebSocketHandler CrdtWebSocketHandler) {
        this.CrdtWebSocketHandler = CrdtWebSocketHandler;
        logger.info("WebSocketConfig initialized with handler: {}", CrdtWebSocketHandler.getClass().getName());
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        String path = "/crdt/{documentId}";
        logger.info("Registering WebSocket handler at path: {}", path);
        logger.info("Server port: {}, Context path: '{}'", serverPort, contextPath);
        
        registry.addHandler(CrdtWebSocketHandler, path)
                .addInterceptors(documentHandshakeInterceptor())
                .setAllowedOrigins("*");
                
        logger.info("WebSocket handler registration complete");
    }

    private HandshakeInterceptor documentHandshakeInterceptor() {
        return new HttpSessionHandshakeInterceptor() {
            @Override
            public boolean beforeHandshake(org.springframework.http.server.ServerHttpRequest request,
                                          org.springframework.http.server.ServerHttpResponse response,
                                          org.springframework.web.socket.WebSocketHandler wsHandler,
                                          Map<String, Object> attributes) throws Exception {
                logger.info("Handshake interceptor called with URI: {}", request.getURI());
                
                // Extract document ID from URI path
                String path = request.getURI().getPath();
                String documentId = path.substring(path.lastIndexOf('/') + 1);
                attributes.put("documentId", documentId);
                logger.info("Extracted documentId: {}", documentId);
                
                // Extract user ID from HTTP session or set a default for testing
                if (request.getPrincipal() != null) {
                    attributes.put("userId", request.getPrincipal().getName());
                    logger.info("Extracted userId from principal: {}", request.getPrincipal().getName());
                } else {
                    // For testing only - this allows connections without authentication
                    attributes.put("userId", "anonymous-user");
                    logger.info("Set default userId for testing: anonymous-user");
                }
                
                return super.beforeHandshake(request, response, wsHandler, attributes);
            }
        };
    }
    
    // Configure WebSocket server parameters
    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(8192);
        container.setMaxBinaryMessageBufferSize(8192);
        container.setMaxSessionIdleTimeout(60000L);
        logger.info("WebSocket container configured with max message size: 8192");
        return container;
    }
    
    // Log when the application context is fully initialized
    @EventListener
    public void handleContextRefresh(ContextRefreshedEvent event) {
        logger.info("Application context refreshed");
        logger.info("WebSocket should be available at: ws://localhost:{}{}/crdt/{{documentId}}",
                    serverPort, contextPath);
    }
}