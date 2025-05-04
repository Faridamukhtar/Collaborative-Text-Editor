package com.collab.backend.server;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles WebSocket connections and messages for collaborative editing.
 */
@Component
public class WebSocketServer extends TextWebSocketHandler {

    /**
     * Thread-safe map to track active sessions by their ID.
     */
    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String sessionId = session.getId();
        sessions.put(sessionId, session);
        System.out.println("[WebSocket] New connection established: " + sessionId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String sessionId = session.getId();
        String payload = message.getPayload();
        System.out.println("[WebSocket] Message received from " + sessionId + ": " + payload);

        // Add your message handling logic here
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String sessionId = session.getId();
        sessions.remove(sessionId);
        System.out.println("[WebSocket] Connection closed: " + sessionId + " - Status: " + status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        String sessionId = session.getId();
        System.err.println("[WebSocket] Transport error on session " + sessionId + ": " + exception.getMessage());
    }

    /**
     * Optional: expose active sessions for broadcasting.
     */
    public ConcurrentHashMap<String, WebSocketSession> getSessions() {
        return sessions;
    }
}
