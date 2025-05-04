package com.example.collaborative_editor.backend.websocket;

import com.example.collaborative_editor.backend.crdt.*;
import com.example.collaborative_editor.backend.service.DocumentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.CloseStatus;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

public class EditorWebSocketHandler extends TextWebSocketHandler {
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Autowired
    private DocumentService documentService;
    
    // Map of documentId -> list of sessions
    private final Map<String, Set<WebSocketSession>> documentSessions = new ConcurrentHashMap<>();
    
    // Map of sessionId -> userId
    private final Map<String, String> sessionUsers = new ConcurrentHashMap<>();
    
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        WebSocketMessage wsMessage = objectMapper.readValue(message.getPayload(), WebSocketMessage.class);
        
        // Handle join document message
        if ("JOIN".equals(wsMessage.getType())) {
            handleJoinDocument(session, wsMessage);
            return;
        }
        
        // Handle operation messages (INSERT, DELETE)
        String documentId = wsMessage.getDocumentId();
        if (documentId == null || !documentSessions.containsKey(documentId)) {
            return;
        }
        
        // Get document from service
        CrdtDocument document = documentService.getCrdtDocumentById(documentId);
        
        // Process operation
        Operation operation = convertToOperation(wsMessage);
        document.applyOperation(operation);
        
        // Save document state
        documentService.saveCrdtDocument(documentId, document);
        
        // Broadcast to all other sessions for this document
        broadcastMessage(wsMessage, session, documentId);
    }
    
    private void handleJoinDocument(WebSocketSession session, WebSocketMessage wsMessage) throws IOException {
        String documentId = wsMessage.getDocumentId();
        String userId = wsMessage.getUserId();
        
        // Store user info
        sessionUsers.put(session.getId(), userId);
        
        // Add session to document
        documentSessions.computeIfAbsent(documentId, k -> ConcurrentHashMap.newKeySet()).add(session);
        
        // Get document from service
        CrdtDocument document = documentService.getCrdtDocumentById(documentId);
        
        // Send document state to the joining client
        WebSocketMessage stateMessage = new WebSocketMessage();
        stateMessage.setType("DOCUMENT_STATE");
        stateMessage.setDocumentId(documentId);
        stateMessage.setContent(document.getStateAsJson());
        
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(stateMessage)));
        
        // Notify other users about the new user joining
        WebSocketMessage joinNotification = new WebSocketMessage();
        joinNotification.setType("USER_JOINED");
        joinNotification.setDocumentId(documentId);
        joinNotification.setUserId(userId);
        
        broadcastMessage(joinNotification, session, documentId);
    }
    
    private Operation convertToOperation(WebSocketMessage message) {
        WebSocketMessage.CharacterDTO charDTO = message.getCharacter();
        
        // Convert position DTOs to Identifiers
        List<Identifier> position = new ArrayList<>();
        for (WebSocketMessage.PositionDTO posDTO : charDTO.getPosition()) {
            position.add(new Identifier(posDTO.getDigit(), posDTO.getSiteId()));
        }
        
        // Create CRDT character
        CrdtChar crdtChar = new CrdtChar(
                charDTO.getValue(), 
                position,
                message.getUserId(),
                message.getTimestamp()
        );
        
        if (charDTO.isDeleted()) {
            crdtChar.setDeleted(true);
        }
        
        // Create operation
        Operation.Type type = Operation.Type.valueOf(message.getType());
        return new Operation(
                type,
                crdtChar,
                message.getUserId(),
                message.getSessionId(),
                message.getTimestamp()
        );
    }
    
    private void broadcastMessage(WebSocketMessage message, WebSocketSession senderSession, String documentId) {
        Set<WebSocketSession> sessions = documentSessions.get(documentId);
        if (sessions != null) {
            try {
                String messageJson = objectMapper.writeValueAsString(message);
                for (WebSocketSession session : sessions) {
                    if (session != senderSession && session.isOpen()) {
                        session.sendMessage(new TextMessage(messageJson));
                    }
                }
            } catch (IOException e) {
                // Log error - could not broadcast message
                e.printStackTrace();
            }
        }
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String userId = sessionUsers.remove(session.getId());
        
        // Remove session from all document sessions
        for (Map.Entry<String, Set<WebSocketSession>> entry : documentSessions.entrySet()) {
            String documentId = entry.getKey();
            Set<WebSocketSession> sessions = entry.getValue();
            
            if (sessions.remove(session) && userId != null) {
                // Notify other users that this user has left
                WebSocketMessage leaveMessage = new WebSocketMessage();
                leaveMessage.setType("USER_LEFT");
                leaveMessage.setDocumentId(documentId);
                leaveMessage.setUserId(userId);
                
                broadcastMessage(leaveMessage, null, documentId);
            }
            
            // If no sessions left for this document, we could consider cleanup
            if (sessions.isEmpty()) {
                documentSessions.remove(documentId);
            }
        }
        
        super.afterConnectionClosed(session, status);
    }
}