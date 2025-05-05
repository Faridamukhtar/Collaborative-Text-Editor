package com.collab.backend.websocket;

import com.collab.backend.crdt.*;
import com.collab.backend.models.DocumentModel;
import com.collab.backend.models.UserModel;
import com.collab.backend.service.DocumentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CrdtWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private DocumentService documentService;

    // Track WebSocket sessions per document
    private final Map<String, Set<WebSocketSession>> documentSessions = new ConcurrentHashMap<>();

    // Track userId per session
    private final Map<WebSocketSession, String> sessionToUserId = new ConcurrentHashMap<>();

    // Track documentId per session
    private final Map<WebSocketSession, String> sessionToDocumentId = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        System.out.println("WebSocket connection established: " + session.getId());

        String query = session.getUri() != null ? session.getUri().getQuery() : null;
        String documentId = extractQueryParam(query, "documentId");
        String userId = extractQueryParam(query, "userId");

        if (documentId == null || userId == null) {
            System.err.println("Missing documentId or userId in query.");
            return;
        }

        DocumentModel doc = documentService.getDocumentById(documentId);
        if (doc == null) {
            System.err.println("Invalid documentId: " + documentId);
            return;
        }

        if (!doc.getUsers().containsKey(userId)) {
            System.err.println("UserId not part of document: " + userId);
            return;
        }

        documentSessions.computeIfAbsent(documentId, k -> ConcurrentHashMap.newKeySet()).add(session);
        sessionToUserId.put(session, userId);
        sessionToDocumentId.put(session, documentId);

        try {
            sendUserList(doc, documentSessions.get(documentId));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        System.out.println("WebSocket closed: " + session.getId());

        String documentId = sessionToDocumentId.remove(session);
        String userId = sessionToUserId.remove(session);

        if (documentId != null) {
            Set<WebSocketSession> sessions = documentSessions.get(documentId);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    documentSessions.remove(documentId);
                }
            }

            DocumentModel doc = documentService.getDocumentById(documentId);
            if (doc != null && userId != null) {
                doc.getUsers().remove(userId); // Optional: auto-remove user on disconnect
                try {
                    sendUserList(doc, documentSessions.get(documentId));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        System.out.println("Received message: " + message.getPayload());
        ClientEditRequest req = objectMapper.readValue(message.getPayload(), ClientEditRequest.class);
        if (req.getType() == null) {
            System.err.println("Invalid message type: " + req.getType());
            return;
        }
        String docId = req.getDocumentId();
        String userId = req.getUserId();

        DocumentModel doc = documentService.getDocumentById(docId);
        if (doc == null) {
            System.err.println("Received edit for non-existent document: " + docId);
            return;
        }

        CrdtTree tree = doc.getCrdtTree();
        tree.apply(req);

        String updatedText = tree.getText();
        Set<WebSocketSession> sessions = documentSessions.get(docId);

        TextMessage messageSent = new TextMessage(updatedText);
        System.out.println("Sending updated text" + messageSent + "to " + sessions.size() + " sessions");
        for (WebSocketSession s : sessions) {
            if (s.isOpen() && !s.equals(session)) {
                try {
                    s.sendMessage(messageSent);
                } catch (IOException e) {
                    System.err.println("❌ Error sending raw text: " + e.getMessage());
                }
            }
        }
    }

    private void sendUserList(DocumentModel doc, Set<WebSocketSession> sessions) throws IOException {
        if (sessions == null) return;

        List<String> usernames = doc.getUsers().values().stream()
                .map(UserModel::getUsername)
                .toList();

        Map<String, Object> msg = new HashMap<>();
        msg.put("type", "ACTIVE_USERS");
        msg.put("usernames", usernames);

        String json = objectMapper.writeValueAsString(msg);

        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(json));
            }
        }
    }

    private String extractQueryParam(String query, String key) {
        if (query == null || !query.contains("=")) return null;
        for (String param : query.split("&")) {
            String[] parts = param.split("=");
            if (parts.length == 2 && parts[0].equals(key)) {
                return parts[1];
            }
        }
        return null;
    }
}