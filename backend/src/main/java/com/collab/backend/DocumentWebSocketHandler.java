package com.collab.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

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
                // Load the document into a CRDT
                System.out.println("Loading document into CRDT: " + documentId);
                TreeCrdt crdt = new TreeCrdt();
                documentService.loadDocumentToCrdt(documentId, crdt);
                System.out.println("Document loaded into CRDT: " + crdt);
                return crdt;
            });

            broadcastUserJoined(documentId, userId, session.getId());

            logger.info("User {} connected to document {}", userId, documentId);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String documentId = sessionDocumentMap.get(session.getId());

        if (documentId == null) {
            logger.warn("Session {} not associated with any document", session.getId());
            return;
        }

        System.out.println("Received message: " + message.getPayload());

        WebSocketMessage webSocketMessage = objectMapper.readValue(message.getPayload(), WebSocketMessage.class);

        switch (webSocketMessage.getType()) {
            case OPERATION:
                handleOperation(documentId, webSocketMessage.getOperation(), session);
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
            logger.info("Connection closed for session: {}", session.getId());
        }
    }

    // ========== Core Handlers ==========

    private void handleOperation(String documentId, Operation operation, WebSocketSession senderSession) {
        TreeCrdt crdt = documentCrdts.get(documentId);

        if (crdt != null && crdt.applyOperation(operation)) {
            broadcastOperation(documentId, operation, senderSession.getId());
            documentService.saveDocumentFromCrdt(documentId, crdt);
        }
    }

    private void handleUserPresence(String documentId, UserPresence presence, WebSocketSession session) {
        String userId = presence.getUserId();
        boolean isOnline = presence.isOnline();

        logger.info("User presence received: {} -> {}", userId, isOnline);

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

        // Optional rebroadcast (or only used in afterConnectionEstablished / afterConnectionClosed)
        // broadcast(documentId, new WebSocketMessage(WebSocketMessage.Type.USER_PRESENCE, presence), session.getId());
    }

    private void broadcastOperation(String documentId, Operation operation, String excludeSessionId) {
        WebSocketMessage message = new WebSocketMessage(WebSocketMessage.Type.OPERATION, operation);
        broadcast(documentId, message, excludeSessionId);
    }

    private void broadcastCursorPosition(String documentId, CursorPosition cursorPosition, WebSocketSession senderSession) {
        WebSocketMessage message = new WebSocketMessage(WebSocketMessage.Type.CURSOR_MOVE, cursorPosition);
        broadcast(documentId, message, senderSession.getId());
    }

    private void broadcastUserJoined(String documentId, String userId, String sessionId) {
        UserPresence presence = new UserPresence(userId, true);
        WebSocketMessage message = new WebSocketMessage(WebSocketMessage.Type.USER_PRESENCE, presence);
        broadcast(documentId, message, sessionId);
    }

    private void broadcastUserLeft(String documentId, String userId, String sessionId) {
        UserPresence presence = new UserPresence(userId, false);
        WebSocketMessage message = new WebSocketMessage(WebSocketMessage.Type.USER_PRESENCE, presence);
        broadcast(documentId, message, null); // broadcast to all
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

    private void sendDocumentState(WebSocketSession session, String documentId) {
        TreeCrdt crdt = documentCrdts.get(documentId);

        if (crdt != null) {
            try {
                Document document = documentService.getLiveDocument(documentId, crdt);
                WebSocketMessage message = new WebSocketMessage(WebSocketMessage.Type.SYNC_RESPONSE, document);
                String payload = objectMapper.writeValueAsString(message);
                session.sendMessage(new TextMessage(payload));
                System.out.println("Document state sent to session: " + session.getId());
            } catch (IOException e) {
                logger.error("Failed to send document state", e);
            }
        }
    }

    private String getDocumentIdFromSession(WebSocketSession session) {
        return (String) session.getAttributes().get("documentId");
    }

    private String getUserIdFromSession(WebSocketSession session) {
        return (String) session.getAttributes().get("userId");
    }

    // ========== Inner Message Classes ==========

    public static class WebSocketMessage {
        public enum Type {
            OPERATION,
            CURSOR_MOVE,
            USER_PRESENCE,
            REQUEST_SYNC,
            SYNC_RESPONSE
        }

        private Type type;
        private Operation operation;
        private CursorPosition cursorPosition;
        private UserPresence userPresence;
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

        // Getters & setters
        public Type getType() { return type; }
        public void setType(Type type) { this.type = type; }

        public Operation getOperation() { return operation; }
        public void setOperation(Operation operation) { this.operation = operation; }

        public CursorPosition getCursorPosition() { return cursorPosition; }
        public void setCursorPosition(CursorPosition cursorPosition) { this.cursorPosition = cursorPosition; }

        public UserPresence getUserPresence() { return userPresence; }
        public void setUserPresence(UserPresence userPresence) { this.userPresence = userPresence; }

        public Document getDocument() { return document; }
        public void setDocument(Document document) { this.document = document; }
    }

    public static class CursorPosition {
        private String userId;
        private String nodeId;
        private int offset;

        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }

        public String getNodeId() { return nodeId; }
        public void setNodeId(String nodeId) { this.nodeId = nodeId; }

        public int getOffset() { return offset; }
        public void setOffset(int offset) { this.offset = offset; }
    }

    public static class UserPresence {
        private String userId;
        private boolean online;

        public UserPresence() {}

        public UserPresence(String userId, boolean online) {
            this.userId = userId;
            this.online = online;
        }

        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }

        public boolean isOnline() { return online; }
        public void setOnline(boolean online) { this.online = online; }
    }
}
