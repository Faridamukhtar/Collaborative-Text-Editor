package com.collab.backend.websocket;

import com.collab.backend.crdt.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class CrdtWebSocketHandler extends TextWebSocketHandler {
    private static final long PING_INTERVAL_MS = 30000; // 30 seconds
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    private final Map<String, CrdtTree> documentTrees = new ConcurrentHashMap<>();
    private final Map<String, Set<WebSocketSession>> documentSessions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService pingScheduler = Executors.newSingleThreadScheduledExecutor();

    @PostConstruct
    public void init() {
        pingScheduler.scheduleAtFixedRate(this::sendPings, PING_INTERVAL_MS, PING_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void cleanup() {
        pingScheduler.shutdown();
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        try {
            String documentId = (String) session.getAttributes().get("documentId");
            if (documentId == null) {
                session.close(CloseStatus.BAD_DATA.withReason("Missing document ID"));
                return;
            }

            sessions.add(session);
            documentSessions.computeIfAbsent(documentId, k -> ConcurrentHashMap.newKeySet()).add(session);
            
            // Send initial document state
            CrdtTree tree = documentTrees.computeIfAbsent(documentId, k -> new CrdtTree());
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(tree.getText())));
            
        } catch (Exception e) {
            try {
                session.close(CloseStatus.SERVER_ERROR.withReason("Connection setup failed"));
            } catch (IOException ex) {
                System.err.println("Failed to close session: " + ex.getMessage());
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String documentId = (String) session.getAttributes().get("documentId");
        if (documentId != null) {
            documentSessions.computeIfPresent(documentId, (k, v) -> {
                v.remove(session);
                return v.isEmpty() ? null : v;
            });
        }
        sessions.remove(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            ClientEditRequest edit = objectMapper.readValue(message.getPayload(), ClientEditRequest.class);
            String docId = edit.getDocumentId();
            
            if (docId == null || !documentSessions.containsKey(docId)) {
                session.close(CloseStatus.BAD_DATA.withReason("Invalid document ID"));
                return;
            }

            CrdtTree tree = documentTrees.computeIfAbsent(docId, k -> new CrdtTree());
            tree.apply(edit);

            // Broadcast update
            String update = objectMapper.writeValueAsString(tree.getText());
            Set<WebSocketSession> docSessions = documentSessions.get(docId);
            
            if (docSessions != null) {
                docSessions.stream()
                    .filter(s -> s.isOpen() && !s.getId().equals(session.getId()))
                    .forEach(s -> sendAsync(s, update));
            }
            
        } catch (Exception e) {
            System.err.println("Error processing message: " + e.getMessage());
            session.close(CloseStatus.BAD_DATA.withReason("Invalid message format"));
        }
    }

    private void sendAsync(WebSocketSession session, String message) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(message));
            }
        } catch (IOException e) {
            System.err.println("Failed to send message to session " + session.getId() + ": " + e.getMessage());
            try {
                session.close(CloseStatus.SERVER_ERROR);
            } catch (IOException ex) {
                System.err.println("Failed to close broken session: " + ex.getMessage());
            }
        }
    }

    private void sendPings() {
        sessions.forEach(session -> {
            try {
                if (session.isOpen()) {
                    session.sendMessage(new PingMessage());
                }
            } catch (IOException e) {
                System.err.println("Failed to send ping to session " + session.getId() + ": " + e.getMessage());
                try {
                    session.close(CloseStatus.SESSION_NOT_RELIABLE);
                } catch (IOException ex) {
                    System.err.println("Failed to close broken session: " + ex.getMessage());
                }
            }
        });
    }
}