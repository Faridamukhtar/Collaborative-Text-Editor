package com.example.application.connections.CRDT;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.websocket.*;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;

@Service
@ClientEndpoint
public class CollaborativeEditService {

    private Session session;
    private final ObjectMapper mapper = new ObjectMapper();
    private static final AtomicReference<CollaborativeEditUiListener> uiListener = new AtomicReference<>();

    public static void setUiListener(CollaborativeEditUiListener listener) {
        uiListener.set(listener);
    }

    public void connectWebSocket(String documentId, String userId) {
        try {
            System.out.println("Connecting to WebSocket..." +documentId);
            String wsUrl = String.format("ws://localhost:8081/crdt/%s?documentId=%s&userId=%s", documentId, documentId, userId);
            WebSocketContainer container = ContainerProvider.getWebSocketContainer();
            container.connectToServer(this, new URI(wsUrl));
            System.out.println("✅ WebSocket connection initialized to: " + wsUrl);
        } catch (Exception e) {
            throw new RuntimeException("❌ WebSocket connection failed", e);
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
        CollaborativeEditUiListener listener = uiListener.get();
        if (listener != null) {
            listener.onServerMessage(message);
        }
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        System.err.println("WebSocket Error: " + throwable.getMessage());
    }

    @OnClose
    public void onClose(Session session, CloseReason reason) {
        System.out.println("WebSocket closed: " + reason);
        // If the WebSocket was closed due to idle timeout or other non-fatal reasons, reconnect
        if (reason.getCloseCode() == CloseReason.CloseCodes.NORMAL_CLOSURE) {
            System.out.println("Attempting to reconnect...");
            reconnect(session.getRequestURI().getPath().split("/")[2]);
        }
    }

    private void reconnect(String documentId) {
        try {
            // Wait for 5 seconds before reconnecting
            Thread.sleep(5000);
            connectWebSocket(documentId); // Attempt to reconnect
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
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
}
