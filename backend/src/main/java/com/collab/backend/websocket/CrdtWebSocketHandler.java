package com.collab.backend.websocket;

import com.collab.backend.CRDT.*;
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
        sessions.add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        String payload = message.getPayload();

        CrdtOperation op = objectMapper.readValue(payload, CrdtOperation.class);
        crdtTree.apply(op);

        String updatedText = objectMapper.writeValueAsString(crdtTree.getText());

        for (WebSocketSession s : sessions) {
            if (s.isOpen() && s != session) {
                s.sendMessage(new TextMessage(updatedText));
            }
        }
    }
} 
