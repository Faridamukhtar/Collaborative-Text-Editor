package com.example.application.connections.model;

import javax.websocket.Session; // Import WebSocket Session
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class CollaborativeSession {  // Rename class to CollaborativeSession
    private String sessionId;  // Unique identifier for the session
    private List<User> activeUsers;  // List of users in the session
    private OperationBuffer operationBuffer;  // Buffer to store missed operations
    private javax.websocket.Session websocketSession;  // WebSocket session for communication

    public CollaborativeSession(String sessionId, javax.websocket.Session websocketSession) {
        this.sessionId = sessionId;
        this.activeUsers = new CopyOnWriteArrayList<>();  // Thread-safe list
        this.operationBuffer = new OperationBuffer();  // Store missed operations
        this.websocketSession = websocketSession;  // Initialize WebSocket session
    }

    // Methods to manage users in the session
    public void addUser(User user) {
        activeUsers.add(user);
    }

    public void removeUser(User user) {
        activeUsers.remove(user);
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

    public javax.websocket.Session getWebSocketSession() {
        return websocketSession;
    }

    // Method to broadcast an edit to all active users in the session
    public void broadcastEdit(String editOperation) {
        // Sending the edit operation to the WebSocket session
        for (User user : activeUsers) {
            if (websocketSession != null) {
                try {
                    websocketSession.getBasicRemote().sendText(editOperation); // Send the edit operation to the client
                } catch (Exception e) {
                    System.err.println("Error sending edit operation: " + e.getMessage());
                }
            }
        }
    }

    // Method to send buffered operations to a reconnecting user
    public void sendBufferedOperations(User user) {
        List<String> missedOps = operationBuffer.getOperations();
        for (String op : missedOps) {
            if (websocketSession != null) {
                try {
                    websocketSession.getBasicRemote().sendText(op);  // Send missed operation to the user
                } catch (Exception e) {
                    System.err.println("Error sending missed operation: " + e.getMessage());
                }
            }
        }
    }
}
