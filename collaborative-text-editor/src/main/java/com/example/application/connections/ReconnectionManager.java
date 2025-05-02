package com.example.application.connections;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.example.application.connections.model.User;
import com.example.application.connections.model.CollaborativeSession;

public class ReconnectionManager {
    private ConcurrentHashMap<String, User> reconnectingUsers = new ConcurrentHashMap<>();
    private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    // Start a timer for reconnection with a 5-minute timeout
    public void startReconnectionTimer(String userId, Runnable onTimeout) {
        scheduler.schedule(() -> {
            reconnectingUsers.remove(userId);
            onTimeout.run();
        }, 5, TimeUnit.MINUTES);
    }

    // Mark user as reconnecting
    public void markUserReconnecting(User user) {
        reconnectingUsers.put(user.getId(), user);
    }

    // Complete the reconnection process and retrieve the user
    public User completeReconnection(String userId) {
        return reconnectingUsers.remove(userId);
    }

    // Send missed operations to the user after they reconnect
    public void sendMissedOperations(javax.websocket.Session websocketSession, User user) {
        String sessionCode = "someSessionCode";  // You need to retrieve the session code from somewhere
        CollaborativeSession collaborativeSession = ConnectionManager.getSession(sessionCode);  // Use the static method

        if (collaborativeSession != null) {
            collaborativeSession.sendBufferedOperations(user);
        } else {
            System.err.println("Collaborative session not found for session code: " + sessionCode);
        }
    }
}
