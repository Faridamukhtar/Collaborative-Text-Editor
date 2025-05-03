package com.collab.backend;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DocumentWebSocketHandler extends TextWebSocketHandler {
    private static final Logger logger = LoggerFactory.getLogger(DocumentWebSocketHandler.class);

    private final Map<String, Map<String, WebSocketSession>> documentSessions = new ConcurrentHashMap<>();
    private final Map<String, String> sessionDocumentMap = new ConcurrentHashMap<>();
    private final Map<String, TreeCrdt> documentCrdts = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> documentOnlineUsers = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper;
    private final DocumentService documentService;

    @Autowired
    public DocumentWebSocketHandler(ObjectMapper objectMapper, DocumentService documentService) {
        this.objectMapper = objectMapper;
        this.documentService = documentService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String documentId = getDocumentIdFromSession(session);
        String userId = getUserIdFromSession(session);

        if (documentId != null && userId != null) {
            documentSessions
                .computeIfAbsent(documentId, k -> new ConcurrentHashMap<>())
                .put(session.getId(), session);

            sessionDocumentMap.put(session.getId(), documentId);

            documentCrdts.computeIfAbsent(documentId, k -> {
                TreeCrdt crdt = new TreeCrdt();
                documentService.loadDocumentToCrdt(documentId, crdt);
                return crdt;
            });

            broadcastUserJoined(documentId, userId, session.getId());
            logger.info("User {} connected to document {}", userId, documentId);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        String documentId = sessionDocumentMap.get(session.getId());
        if (documentId == null) {
            logger.warn("Session {} not associated with any document", session.getId());
            session.close(CloseStatus.BAD_DATA.withReason("No document associated"));
            return;
        }

        try {
            WebSocketMessage webSocketMessage = objectMapper.readValue(message.getPayload(), WebSocketMessage.class);
            
            if (webSocketMessage == null || webSocketMessage.getType() == null) {
                session.sendMessage(new TextMessage("{\"error\":\"Invalid message format\"}"));
                return;
            }

            switch (webSocketMessage.getType()) {
                case OPERATION:
                    if (validateOperation(webSocketMessage.getOperation())) {
                        handleOperation(documentId, webSocketMessage.getOperation(), session);
                    } else {
                        session.sendMessage(new TextMessage("{\"error\":\"Invalid operation format\"}"));
                    }
                    break;
                case CURSOR_MOVE:
                    broadcastCursorPosition(documentId, webSocketMessage.getCursorPosition(), session);
                    break;
                case REQUEST_SYNC:
                    sendDocumentState(session, documentId);
                    break;
                case USER_PRESENCE:
                    handleUserPresence(documentId, webSocketMessage.getUserPresence(), session);
                    break;
                default:
                    logger.warn("Unknown message type: {}", webSocketMessage.getType());
                    session.sendMessage(new TextMessage("{\"error\":\"Unknown message type\"}"));
            }
        } catch (JsonProcessingException e) {
            logger.error("JSON parsing error", e);
            session.sendMessage(new TextMessage("{\"error\":\"Invalid JSON format\"}"));
        } catch (Exception e) {
            logger.error("Error processing message", e);
            session.sendMessage(new TextMessage("{\"error\":\"Server error: " + e.getMessage() + "\"}"));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String documentId = sessionDocumentMap.get(session.getId());
        if (documentId != null) {
            Map<String, WebSocketSession> sessions = documentSessions.get(documentId);
            if (sessions != null) {
                String userId = getUserIdFromSession(session);
                sessions.remove(session.getId());

                if (userId != null) {
                    broadcastUserLeft(documentId, userId, session.getId());
                }

                if (sessions.isEmpty()) {
                    TreeCrdt crdt = documentCrdts.remove(documentId);
                    if (crdt != null) {
                        documentService.saveDocumentFromCrdt(documentId, crdt);
                    }
                    documentSessions.remove(documentId);
                    documentOnlineUsers.remove(documentId);
                }
            }
            sessionDocumentMap.remove(session.getId());
            logger.info("Connection closed for session {} with status {}", session.getId(), status);
        }
    }

    private boolean validateOperation(Operation operation) {
        return operation != null 
               && operation.getType() != null
               && operation.getUserId() != null
               && (operation.getType() != Operation.Type.INSERT || 
                  (operation.getParentId() != null && operation.getContent() != null));
    }

    private void handleOperation(String documentId, Operation operation, WebSocketSession session) {
        try {
            if (operation == null || operation.getType() == null) {
                logger.warn("Received invalid operation");
                return;
            }
    
            TreeCrdt crdt = documentCrdts.get(documentId);
            if (crdt == null) {
                logger.error("No CRDT found for document {}", documentId);
                return;
            }
    
            if (!crdt.applyOperation(operation)) {
                logger.warn("Failed to apply operation: {}", operation);
                return;
            }
    
            broadcastOperation(documentId, operation, session.getId());
            documentService.saveDocumentFromCrdt(documentId, crdt);
        } catch (Exception e) {
            logger.error("Error handling operation", e);
        }
    }

    private void handleUserPresence(String documentId, UserPresence presence, WebSocketSession session) {
        String userId = presence.getUserId();
        boolean isOnline = presence.isOnline();

        if (isOnline) {
            documentOnlineUsers
                .computeIfAbsent(documentId, k -> ConcurrentHashMap.newKeySet())
                .add(userId);
        } else {
            Set<String> onlineUsers = documentOnlineUsers.get(documentId);
            if (onlineUsers != null) {
                onlineUsers.remove(userId);
            }
        }
    }

    private void broadcastOperation(String documentId, Operation operation, String excludeSessionId) {
        broadcast(documentId, new WebSocketMessage(WebSocketMessage.Type.OPERATION, operation), excludeSessionId);
    }

    private void broadcastCursorPosition(String documentId, CursorPosition cursorPosition, WebSocketSession senderSession) {
        broadcast(documentId, new WebSocketMessage(WebSocketMessage.Type.CURSOR_MOVE, cursorPosition), senderSession.getId());
    }

    private void broadcastUserJoined(String documentId, String userId, String sessionId) {
        broadcast(documentId, new WebSocketMessage(WebSocketMessage.Type.USER_PRESENCE, new UserPresence(userId, true)), sessionId);
    }

    private void broadcastUserLeft(String documentId, String userId, String sessionId) {
        broadcast(documentId, new WebSocketMessage(WebSocketMessage.Type.USER_PRESENCE, new UserPresence(userId, false)), null);
    }

    private void broadcast(String documentId, WebSocketMessage message, String excludeSessionId) {
        Map<String, WebSocketSession> sessions = documentSessions.get(documentId);
        if (sessions != null) {
            try {
                String payload = objectMapper.writeValueAsString(message);
                TextMessage textMessage = new TextMessage(payload);

                for (Map.Entry<String, WebSocketSession> entry : sessions.entrySet()) {
                    if (excludeSessionId == null || !entry.getKey().equals(excludeSessionId)) {
                        try {
                            entry.getValue().sendMessage(textMessage);
                        } catch (IOException e) {
                            logger.error("Failed to send message to session {}", entry.getKey(), e);
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("Failed to serialize message", e);
            }
        }
    }

    private void sendDocumentState(WebSocketSession session, String documentId) throws IOException {
        TreeCrdt crdt = documentCrdts.get(documentId);
        if (crdt != null) {
            Document document = documentService.getLiveDocument(documentId, crdt);
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(
                new WebSocketMessage(WebSocketMessage.Type.SYNC_RESPONSE, document)
            )));
        }
    }

    private String getDocumentIdFromSession(WebSocketSession session) {
        return (String) session.getAttributes().get("documentId");
    }

    private String getUserIdFromSession(WebSocketSession session) {
        return (String) session.getAttributes().get("userId");
    }

    public static class WebSocketMessage {
        public enum Type {
            @JsonProperty("OPERATION") OPERATION,
            @JsonProperty("CURSOR_MOVE") CURSOR_MOVE,
            @JsonProperty("USER_PRESENCE") USER_PRESENCE,
            @JsonProperty("REQUEST_SYNC") REQUEST_SYNC,
            @JsonProperty("SYNC_RESPONSE") SYNC_RESPONSE
        }

        @JsonProperty("type")
        private Type type;
        
        @JsonProperty("operation")
        private Operation operation;
        
        @JsonProperty("cursorPosition")
        private CursorPosition cursorPosition;
        
        @JsonProperty("userPresence")
        private UserPresence userPresence;
        
        @JsonProperty("document")
        private Document document;

        public WebSocketMessage() {}

        public WebSocketMessage(Type type, Operation operation) {
            this.type = type;
            this.operation = operation;
        }

        public WebSocketMessage(Type type, CursorPosition cursorPosition) {
            this.type = type;
            this.cursorPosition = cursorPosition;
        }

        public WebSocketMessage(Type type, UserPresence userPresence) {
            this.type = type;
            this.userPresence = userPresence;
        }

        public WebSocketMessage(Type type, Document document) {
            this.type = type;
            this.document = document;
        }

        public Type getType() { return type; }
        public Operation getOperation() { return operation; }
        public CursorPosition getCursorPosition() { return cursorPosition; }
        public UserPresence getUserPresence() { return userPresence; }
        public Document getDocument() { return document; }
    }

    public static class CursorPosition {
        @JsonProperty("userId")
        private String userId;
        
        @JsonProperty("nodeId")
        private String nodeId;
        
        @JsonProperty("offset")
        private int offset;

        public String getUserId() { return userId; }
        public String getNodeId() { return nodeId; }
        public int getOffset() { return offset; }
    }

    public static class UserPresence {
        @JsonProperty("userId")
        private String userId;
        
        @JsonProperty("online")
        private boolean online;

        public UserPresence() {}

        public UserPresence(String userId, boolean online) {
            this.userId = userId;
            this.online = online;
        }

        public String getUserId() { return userId; }
        public boolean isOnline() { return online; }
    }
}