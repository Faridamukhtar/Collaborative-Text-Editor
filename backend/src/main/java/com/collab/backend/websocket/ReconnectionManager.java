package com.collab.backend.websocket;

import java.util.concurrent.*;
import java.util.*;

public class ReconnectionManager {
    private static final long RECONNECT_WINDOW_MS = 5 * 60 * 1000;

    // userId -> disconnect time
    private static final Map<String, Long> reconnectingUsers = new ConcurrentHashMap<>();

    // userId -> missed operations
    private static final Map<String, List<ClientEditRequest>> missedOperations = new ConcurrentHashMap<>();

    public static void markReconnecting(String userId) {
        reconnectingUsers.put(userId, System.currentTimeMillis());
    }

    public static void saveMissedOperation(String userId, ClientEditRequest op) {
        missedOperations.computeIfAbsent(userId, k -> new ArrayList<>()).add(op);
    }

    public static List<ClientEditRequest> getMissedOperations(String userId) {
        return missedOperations.getOrDefault(userId, Collections.emptyList());
    }

    public static void clear(String userId) {
        reconnectingUsers.remove(userId);
        missedOperations.remove(userId);
    }

    public static boolean isInWindow(String userId) {
        return reconnectingUsers.containsKey(userId)
                && (System.currentTimeMillis() - reconnectingUsers.get(userId) <= RECONNECT_WINDOW_MS);
    }
}
