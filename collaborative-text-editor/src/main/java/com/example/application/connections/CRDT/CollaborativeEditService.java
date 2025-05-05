package com.example.application.connections.CRDT;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.websocket.*;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;

@Service
@ClientEndpoint
public class CollaborativeEditService {

    private Session session;
    private final ObjectMapper mapper = new ObjectMapper();

    public CollaborativeEditService() {
        // Default constructor
    }

    private static CollaborativeEditUiListener uiListener;

    public static void setUiListener(CollaborativeEditUiListener listener) {
        uiListener = listener;
    }


    @EventListener(ApplicationReadyEvent.class)
    public void connectWebSocket() {
        try {
            WebSocketContainer container = ContainerProvider.getWebSocketContainer();
            container.connectToServer(this, new URI("ws://localhost:8081/ws/crdt"));
            System.out.println("✅ WebSocket connection initialized after app start");
        } catch (Exception e) {
            throw new RuntimeException("WebSocket connection failed", e);
        }
    }

    @OnOpen
    public void onOpen(Session session) {
        this.session = session;
        System.out.println("🔗 Connected to CRDT WebSocket server");
    }

    @OnMessage
    public void onMessage(String message) {
        System.out.println("📩 Message received from server: " + message);
        if (uiListener != null) {
            System.out.println("📩 Notifying UI listener about message");
            uiListener.onServerMessage(message);
        }

    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        System.err.println("❌ WebSocket Error: " + throwable.getMessage());
    }

    @OnClose
    public void onClose(Session session, CloseReason reason) {
        System.out.println("⚠️ WebSocket closed: " + reason);
    }

    public void sendEditRequest(ClientEditRequest req) {
        if (session != null && session.isOpen()) {
            try {
                String json = mapper.writeValueAsString(req);
                session.getAsyncRemote().sendText(json);
                System.out.println("📤 Sent edit request: " + json);
            } catch (IOException e) {
                throw new RuntimeException("Failed to send WebSocket message", e);
            }
        } else {
            System.err.println("❗ Cannot send. WebSocket session is closed or null.");
        }
    }

    public static ClientEditRequest createInsertRequest(String value, int position, String userId) {
        ClientEditRequest req = new ClientEditRequest();
        req.type = ClientEditRequest.Type.INSERT;
        req.value = value;
        req.position = position;
        req.timestamp = System.currentTimeMillis();
        req.userId = userId;
        req.documentId = "default";
        return req;
    }

    public static ClientEditRequest createDeleteRequest(int position, String userId) {
        ClientEditRequest req = new ClientEditRequest();
        req.type = ClientEditRequest.Type.DELETE;
        req.position = position;
        req.timestamp = System.currentTimeMillis();
        req.userId = userId;
        req.documentId = "default";
        return req;
    }
}
