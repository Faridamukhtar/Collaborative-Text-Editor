package com.collab.backend.websocket;

import com.collab.backend.CRDT.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.CloseStatus;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
@Component
public class CrdtWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    private final CrdtTree crdtTree = new CrdtTree(); // Still single-document mode
    private final Map<WebSocketSession, String> sessionToUsername = new ConcurrentHashMap<>();
    private final String docId = "1";

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        System.out.println("WebSocket connection established: " + session.getId());

        // Extract username from query string
        String query = session.getUri().getQuery(); // e.g., userId=ahmed
        String username = extractQueryParam(query, "userId");
        if (username == null || username.isBlank()) {
            username = "anonymous_" + session.getId().substring(0, 5);
        }

        sessionToUsername.put(session, username);
        sessions.add(session);

        try {
            sendUserList();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        System.out.println("WebSocket closed: " + session.getId());
        sessions.remove(session);
        sessionToUsername.remove(session);

        try {
            sendUserList();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        System.out.println("Message received: " + message.getPayload());

        ClientEditRequest clientEditRequest = objectMapper.readValue(message.getPayload(), ClientEditRequest.class);
        crdtTree.apply(clientEditRequest);

        String updatedText = objectMapper.writeValueAsString(crdtTree.getText());

        for (WebSocketSession s : sessions) {
            if (s.isOpen()) {
                s.sendMessage(new TextMessage(updatedText));
            }
        }
    }

    private void sendUserList() throws IOException {
        List<String> usernames = sessions.stream()
            .map(sessionToUsername::get)
            .filter(Objects::nonNull)
            .toList();

        Map<String, Object> msg = new HashMap<>();
        msg.put("type", "ACTIVE_USERS");
        msg.put("usernames", usernames);

        String json = objectMapper.writeValueAsString(msg);

        for (WebSocketSession s : sessions) {
            s.sendMessage(new TextMessage(json));
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
