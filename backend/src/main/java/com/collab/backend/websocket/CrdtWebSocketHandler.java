package com.collab.backend.websocket;

import com.collab.backend.crdt.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.CloseStatus;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CrdtWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    // Map: documentId -> CRDT tree
    private final Map<String, CrdtTree> documentTrees = new ConcurrentHashMap<>();
    // Map: documentId -> sessions for that document
    private final Map<String, Set<WebSocketSession>> documentSessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        System.out.println("New WebSocket connection established: " + session.getId());

        // Assuming the client sends a document ID during connection initiation (perhaps as part of the first message)
        String documentId = getDocumentIdFromSession(session);
        if (documentId != null) {
            session.getAttributes().put("documentId", documentId); // Store document ID in session
        }

        sessions.add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        System.out.println(session);
        System.out.println("WebSocket connection closed: " + session.getId());

        String documentId = getDocumentIdFromSession(session);
        if (documentId != null) {
            Set<WebSocketSession> docSessions = documentSessions.get(documentId);
            if (docSessions != null) {
                docSessions.remove(session);
                if (docSessions.isEmpty()) {
                    documentSessions.remove(documentId);  // Clean up if no sessions are left for this document
                    documentTrees.remove(documentId);     // Also clean up the CRDT tree
                }
            }
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        try {
            // Deserialize the incoming message
            ClientEditRequest clientEditRequest = objectMapper.readValue(message.getPayload(), ClientEditRequest.class);
            String docId = clientEditRequest.getDocumentId();

            // Initialize CRDT tree if not present
            documentTrees.putIfAbsent(docId, new CrdtTree());

            // Track session for this document
            documentSessions.computeIfAbsent(docId, k -> ConcurrentHashMap.newKeySet()).add(session);

            // Apply the client's edit to the CRDT tree
            CrdtTree tree = documentTrees.get(docId);
            tree.apply(clientEditRequest);

            // Serialize the updated text from the CRDT tree
            String updatedText = objectMapper.writeValueAsString(tree.getText());

            // Broadcast updated text to all other sessions for this document (EXCLUDING the sender)
            Set<WebSocketSession> docSessions = documentSessions.get(docId);
            if (docSessions != null) {
                for (WebSocketSession s : docSessions) {
                    if (!s.getId().equals(session.getId())) {  // Skip the sender
                        s.sendMessage(new TextMessage(updatedText));
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error handling message: " + e.getMessage());
        }
    }

    private String getDocumentIdFromSession(WebSocketSession session) {
        return (String) session.getAttributes().get("documentId");
    }

}
