package com.collab.backend.websocket;

import com.collab.backend.crdt.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.CloseStatus;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CrdtWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    private final CrdtTree crdtTree = new CrdtTree(); // TODO: remove to doc initialization AND HANDLE MULTIPLE DOCS

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        System.out.println("New WebSocket connection established: " + session.getId());
        sessions.add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        System.out.println("WebSocket connection closed: " + session.getId());
        // Find the document ID associated with the session (you may need to store this in session attributes)
        String documentId = getDocumentIdFromSession(session);
        if (documentId != null) {
            Set<WebSocketSession> docSessions = documentSessions.get(documentId);
            if (docSessions != null) {
                docSessions.remove(session);
                if (docSessions.isEmpty()) {
                    documentSessions.remove(documentId);  // Clean up if no sessions are left for this document
                }
            }
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        ClientEditRequest clientEditRequest = objectMapper.readValue(message.getPayload(), ClientEditRequest.class);
        String docId = clientEditRequest.getDocumentId();

        // Initialize CRDT tree if not present
        documentTrees.putIfAbsent(docId, new CrdtTree());

        // Track session for this document
        documentSessions.computeIfAbsent(docId, k -> ConcurrentHashMap.newKeySet()).add(session);

        CrdtTree tree = documentTrees.get(docId);
        tree.apply(clientEditRequest);

        // Broadcast updated text to other sessions in the same document (EXCLUDING sender)
        String updatedText = objectMapper.writeValueAsString(tree.getText());
        Set<WebSocketSession> docSessions = documentSessions.get(docId);
        
        if (docSessions != null) {
            for (WebSocketSession s : docSessions) {
                if (!s.getId().equals(session.getId())) {  // Critical: Skip the sender
                    System.out.println("Sending message to session: " + s.getId());
                    s.sendMessage(new TextMessage(updatedText));
                }
            }
        }
    }

    // Helper method to retrieve the document ID from the session (if stored during the connection)
    private String getDocumentIdFromSession(WebSocketSession session) {
        // You can store the documentId as session attributes during connection establishment
        return (String) session.getAttributes().get("documentId");
    }
}
