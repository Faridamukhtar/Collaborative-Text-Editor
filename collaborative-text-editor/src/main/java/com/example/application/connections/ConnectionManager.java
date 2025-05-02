package com.example.application.connections;

import java.util.concurrent.ConcurrentHashMap;
import com.example.application.connections.model.CollaborativeSession;

public class ConnectionManager {
    private static ConcurrentHashMap<String, CollaborativeSession> activeSessions = new ConcurrentHashMap<>();

    // Make this static so it can be accessed globally
    public static void addSession(String code, CollaborativeSession session) {
        activeSessions.put(code, session);
    }

    public static CollaborativeSession getSession(String code) {
        return activeSessions.get(code);
    }

    public static void removeSession(String code) {
        activeSessions.remove(code);
    }

    // Broadcasting edits to all users in a session
    public static void broadcastEdit(String sessionCode, String editOperation) {
        CollaborativeSession session = activeSessions.get(sessionCode);
        if (session != null) {
            session.broadcastEdit(editOperation);
        }
    }
}
