package com.example.application.connections.CRDT;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.websocket.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@ClientEndpoint
public class CollaborativeEditService {

    private static final ObjectMapper mapper = new ObjectMapper();

    private final Map<String, Session> sessionMap = new ConcurrentHashMap<>();
    private final Map<String, CollaborativeEditUiListener> listenerMap = new ConcurrentHashMap<>();

    // Generate a unique key for each user-document session
    private String sessionKey(String documentId, String userId) {
        return userId + "_" + documentId;
    }

    public void registerListener(String documentId, String userId, CollaborativeEditUiListener listener) {
        listenerMap.put(sessionKey(documentId, userId), listener);
    }

    public void unregisterListener(String documentId, String userId) {
        String key = sessionKey(documentId, userId);
        listenerMap.remove(key);
        Session session = sessionMap.remove(key);
        if (session != null && session.isOpen()) {
            try {
                session.close();
                System.out.println("❎ Closed WebSocket session for " + key);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void connectWebSocket(String documentId, String userId) {
        try {
            String key = sessionKey(documentId, userId);
            String wsUrl = String.format("ws://localhost:8081/crdt/%s?documentId=%s&userId=%s", documentId, documentId, userId);
            WebSocketContainer container = ContainerProvider.getWebSocketContainer();
            container.connectToServer(new CollaborativeEditClientEndpoint(documentId, userId), new URI(wsUrl));
            System.out.println("✅ WebSocket connection initialized for " + key);
        } catch (Exception e) {
            throw new RuntimeException("❌ WebSocket connection failed", e);
        }
    }

    public void sendEditRequest(ClientEditRequest req) {
        String key = sessionKey(req.documentId, req.userId);
        Session session = sessionMap.get(key);
        if (session != null && session.isOpen()) {
            try {
                String json = mapper.writeValueAsString(req);
                session.getAsyncRemote().sendText(json);
                System.out.println("📤 Sent edit request: " + json);
            } catch (IOException e) {
                throw new RuntimeException("Failed to send WebSocket message", e);
            }
        } else {
            System.err.println("❗ Cannot send. WebSocket session is closed or null for " + key);
        }
    }

    public static ClientEditRequest createInsertRequest(String value, int position, String userId, String documentId) {
        ClientEditRequest req = new ClientEditRequest();
        req.type = ClientEditRequest.Type.INSERT;
        req.value = value;
        req.position = position;
        req.timestamp = System.currentTimeMillis();
        req.userId = userId;
        req.documentId = documentId;
        return req;
    }

    public static ClientEditRequest createDeleteRequest(int position, String userId, String documentId) {
        ClientEditRequest req = new ClientEditRequest();
        req.type = ClientEditRequest.Type.DELETE;
        req.position = position;
        req.timestamp = System.currentTimeMillis();
        req.userId = userId;
        req.documentId = documentId;
        return req;
    }

    /**
     * Internal WebSocket ClientEndpoint that manages a single document-user session.
     */
    @ClientEndpoint
    public class CollaborativeEditClientEndpoint {

        private final String documentId;
        private final String userId;

        public CollaborativeEditClientEndpoint(String documentId, String userId) {
            this.documentId = documentId;
            this.userId = userId;
        }

        @OnOpen
        public void onOpen(Session session) {
            String key = sessionKey(documentId, userId);
            sessionMap.put(key, session);
            System.out.println("🔗 WebSocket opened for " + key);
        }

        @OnMessage
        public void onMessage(String message) {
            String key = sessionKey(documentId, userId);
            CollaborativeEditUiListener listener = listenerMap.get(key);
            if (listener != null) {
                listener.onServerMessage(message);
            } else {
                System.err.println("⚠ No UI listener for key: " + key);
            }
        }

        @OnClose
        public void onClose(Session session, CloseReason reason) {
            String key = sessionKey(documentId, userId);
            sessionMap.remove(key);
            System.out.println("❎ WebSocket closed for " + key + " - Reason: " + reason);
        }

        @OnError
        public void onError(Session session, Throwable throwable) {
            System.err.println("❌ WebSocket error for " + userId + "_" + documentId + ": " + throwable.getMessage());
        }
    }
}
