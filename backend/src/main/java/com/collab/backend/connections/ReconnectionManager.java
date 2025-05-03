package com.collab.backend.connections;

import com.collab.backend.connections.model.CollaborativeSession;
import com.collab.backend.connections.model.User;
import org.springframework.web.socket.WebSocketSession;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ReconnectionManager {
    private final ConcurrentHashMap<String, User> reconnectingUsers = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public void startReconnectionTimer(String userId, Runnable onTimeout) {
        scheduler.schedule(() -> {
            reconnectingUsers.remove(userId);
            onTimeout.run();
        }, 5, TimeUnit.MINUTES);
    }

    public void markUserReconnecting(User user) {
        reconnectingUsers.put(user.getId(), user);
    }

    public User completeReconnection(String userId) {
        return reconnectingUsers.remove(userId);
    }

    public void sendMissedOperations(WebSocketSession session, User user) {
        // You must associate users with sessionCodes somewhere for this to be accurate
        String sessionCode = user.getId(); // Replace with correct logic to map user to session
        CollaborativeSession collaborativeSession = ConnectionManager.getSession(sessionCode);

        if (collaborativeSession != null) {
            collaborativeSession.sendBufferedOperations(user); // Will use Spring WebSocket inside
        } else {
            System.err.println("Collaborative session not found for session code: " + sessionCode);
        }
    }
}
