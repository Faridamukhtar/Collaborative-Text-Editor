package com.collab.backend.websocket;

import com.collab.backend.crdt.*;
import com.collab.backend.models.CommentModel;
import com.collab.backend.models.DocumentModel;
import com.collab.backend.models.UserModel;
import com.collab.backend.service.DocumentService;
import com.fasterxml.jackson.core.JsonProcessingException;
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

    private final Map<String, Set<WebSocketSession>> documentSessions = new ConcurrentHashMap<>();

    private final Map<WebSocketSession, String> sessionToUserId = new ConcurrentHashMap<>();

    private final Map<WebSocketSession, String> sessionToDocumentId = new ConcurrentHashMap<>();

    private final Map<String, Integer> trackCursors = new ConcurrentHashMap<>();


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

        documentSessions.computeIfAbsent(documentId, _ -> ConcurrentHashMap.newKeySet()).add(session);
        sessionToUserId.put(session, userId);
        sessionToDocumentId.put(session, documentId);

        try {
            sendUserList(doc, documentSessions.get(documentId));
            session.sendMessage(new TextMessage(doc.getCrdtTree().getText()));

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
                doc.getUsers().remove(userId); 
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
        else if (req.getType() == ClientEditRequest.Type.CURSOR) {
            updateCursor(session, req);
            return;

        }
        String docId = req.getDocumentId();

        String userId = req.getUserId();
        DocumentModel doc = documentService.getDocumentById(docId);
        if (doc == null) {
            System.err.println("Received edit for non-existent document: " + docId);
            return;
        }

        // ✅ Handle new comment addition
        if (req.getType() == ClientEditRequest.Type.ADD_COMMENT) {
            CommentModel comment = new CommentModel(
                req.getUserId(),
                req.getCommentId(),
                req.getValue(),
                req.getPosition(),
                req.getEndPosition()
            );

            doc.addComment(comment);

            String responseJson = String.format(
                "{\"type\":\"commentAdded\",\"commentId\":\"%s\",\"userId\":\"%s\",\"text\":\"%s\",\"startIndex\":%d,\"endIndex\":%d}",
                req.getCommentId(), req.getUserId(), req.getValue(), req.getPosition(), req.getEndPosition()
            );
            TextMessage broadcastMsg = new TextMessage(responseJson);

            for (WebSocketSession s : documentSessions.get(docId)) {
                if (s.isOpen()) s.sendMessage(broadcastMsg);
            }
            return;
        }

        // ✅ Handle comment deletion 
        if (req.getType() == ClientEditRequest.Type.DELETE_COMMENT) {
            doc.removeCommentById(req.getCommentId());

            String deleteMsgJson = String.format(
                "{\"type\":\"commentDeleted\",\"commentId\":\"%s\"}",
                req.getCommentId()
            );
            TextMessage deleteMsg = new TextMessage(deleteMsgJson);

            for (WebSocketSession s : documentSessions.get(docId)) {
                if (s.isOpen()) s.sendMessage(deleteMsg);
            }
            return;
        }

        // ✅ Standard CRDT operation
        CrdtTree tree = doc.getCrdtTree();
        tree.apply(req);

        // ✅ Broadcast updated content
        String updatedText = tree.getText();
        System.out.println("Updated text: " + updatedText);
        Set<WebSocketSession> sessions = documentSessions.get(docId);

        TextMessage messageSent = new TextMessage(updatedText);
        System.out.println("Sending updated text" + messageSent + "to " + sessions.size() + " sessions");
        for (WebSocketSession s : sessions) {
            if (s.isOpen() && !s.equals(session)) {
                try {
                    s.sendMessage(messageSent);
                } catch (IOException e) {
                    System.err.println("Failed to send message to session: " + s.getId());
                    e.printStackTrace();
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

    private void updateCursor(WebSocketSession session, ClientEditRequest req) throws IOException {
        String userId = req.getUserId();
        int position =req.getPosition();
        String documentId = req.getDocumentId();
        trackCursors.put(userId, position);

        Set<WebSocketSession> sessions = documentSessions.get(documentId);
        if (sessions == null) return;

        String cursorUpdateMessage = createCursorUpdateMessage();
        
        sendToSessions(sessions, cursorUpdateMessage);
    }

    private String createCursorUpdateMessage() throws JsonProcessingException {
        System.out.println(trackCursors);
    
        Map<String, Object> cursorMsg = new HashMap<>();
        cursorMsg.put("type", "CURSOR_UPDATE");
        cursorMsg.put("cursors", trackCursors);
        System.out.println(cursorMsg);
    
        return objectMapper.writeValueAsString(cursorMsg);
    }
    
    private void sendToSessions(Set<WebSocketSession> sessions, String message) throws IOException {
        for (WebSocketSession s : sessions) {
            if (s.isOpen()) {
                s.sendMessage(new TextMessage(message));
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