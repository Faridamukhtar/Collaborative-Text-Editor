package com.collab.backend.connections.model;

import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class CollaborativeSession {
    private final String sessionId;
    private final List<User> activeUsers;
    private final OperationBuffer operationBuffer;
    private final List<WebSocketSession> websocketSessions;

    public CollaborativeSession(String sessionId) {
        this.sessionId = sessionId;
        this.activeUsers = new CopyOnWriteArrayList<>();
        this.operationBuffer = new OperationBuffer();
        this.websocketSessions = new CopyOnWriteArrayList<>();
    }

    public void addUser(User user) {
        activeUsers.add(user);
    }

    public void removeUser(User user) {
        activeUsers.remove(user);
    }

    public void addWebSocketSession(WebSocketSession session) {
        websocketSessions.add(session);
    }

    public void removeWebSocketSession(WebSocketSession session) {
        websocketSessions.remove(session);
    }

    public List<User> getActiveUsers() {
        return activeUsers;
    }

    public OperationBuffer getOperationBuffer() {
        return operationBuffer;
    }

    public String getId() {
        return sessionId;
    }

    // Broadcasts to all connected WebSocket sessions
    public void broadcastEdit(String editOperation) {
        for (WebSocketSession session : websocketSessions) {
            if (session != null && session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(editOperation));
                } catch (Exception e) {
                    System.err.println("Error sending edit operation: " + e.getMessage());
                }
            }
        }
    }

    public void sendBufferedOperations(User user) {
        List<String> missedOps = operationBuffer.getOperations();
        for (String op : missedOps) {
            for (WebSocketSession session : websocketSessions) {
                if (session != null && session.isOpen()) {
                    try {
                        session.sendMessage(new TextMessage(op));
                    } catch (Exception e) {
                        System.err.println("Error sending missed operation: " + e.getMessage());
                    }
                }
            }
        }
    }
}
