package com.collab.backend.config;

import com.collab.backend.websocket.CrdtWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

import java.net.URI;
import java.util.Map;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private static final Logger logger = LoggerFactory.getLogger(WebSocketConfig.class);

    @Value("${server.port:8081}")
    private int serverPort;

    @Value("${server.servlet.context-path:}")
    private String contextPath;

    private final CrdtWebSocketHandler crdtWebSocketHandler;

    @Autowired
    public WebSocketConfig(CrdtWebSocketHandler crdtWebSocketHandler) {
        this.crdtWebSocketHandler = crdtWebSocketHandler;
        logger.info("WebSocketConfig initialized with handler: {}", crdtWebSocketHandler.getClass().getName());
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        String path = "/crdt/{documentId}";
        logger.info("Registering WebSocket handler at path: {}", path);
        logger.info("Server port: {}, Context path: '{}'", serverPort, contextPath);

        registry.addHandler(crdtWebSocketHandler, path)
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
                URI uri = request.getURI();
                String query = uri.getQuery(); // get query string: ?documentId=...&userId=...
                String documentId = null;
                String userId = null;
    
                if (query != null) {
                    for (String param : query.split("&")) {
                        String[] parts = param.split("=");
                        if (parts.length == 2) {
                            if (parts[0].equals("documentId")) {
                                documentId = parts[1];
                            } else if (parts[0].equals("userId")) {
                                userId = parts[1];
                            }
                        }
                    }
                }
    
                if (documentId != null) {
                    attributes.put("documentId", documentId);
                    logger.info("Extracted documentId from query: {}", documentId);
                } else {
                    logger.warn("No documentId found in query!");
                }
    
                if (userId != null) {
                    attributes.put("userId", userId);
                    logger.info("Extracted userId from query: {}", userId);
                } else {
                    attributes.put("userId", "anonymous-user");
                    logger.warn("No userId found in query. Using 'anonymous-user'");
                }
    
                return super.beforeHandshake(request, response, wsHandler, attributes);
            }
        };
    }
    

    @EventListener
    public void handleContextRefresh(ContextRefreshedEvent event) {
        logger.info("Application context refreshed");
        logger.info("WebSocket endpoint ready at: ws://localhost:{}{}/crdt/{{documentId}}",
                serverPort, contextPath);
    }
}
