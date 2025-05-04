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
        System.out.println("New WebSocket connection established: " + session.getId());
        sessions.add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        System.out.println("WebSocket connection closed: " + session.getId() + " with status: " + status);
        sessions.remove(session);
    }
    // @Override
    // protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
    //     JsonNode root = objectMapper.readTree(message.getPayload());
    //     ClientEditRequest req = objectMapper.treeToValue(root, ClientEditRequest.class);

    //     CrdtTree tree = documentTrees.get(req.documentId);
    //     CrdtOperation op;

    //     if ("INSERT".equalsIgnoreCase(req.type)) {
    //         op = CrdtOperation.fromClientInsert(req, tree.getVisibleIds());
    //     } else if ("DELETE".equalsIgnoreCase(req.type)) {
    //         op = CrdtOperation.fromClientDelete(req);
    //     } else {
    //         session.sendMessage(new TextMessage("Unsupported operation type"));
    //         return;
    //     }

    //     tree.apply(op);

    //     String text = tree.getText();
    //     String json = objectMapper.writeValueAsString(text);

    //     for (WebSocketSession s : documentSessions.get(req.documentId)) {
    //         if (s.isOpen() && s != session) {
    //             s.sendMessage(new TextMessage(json));
    //         }
    //     }
    // }
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        System.out.println("Received message: " + message.getPayload());


        String payload = message.getPayload();

        ClientEditRequest clientEditRequest = objectMapper.readValue(payload, ClientEditRequest.class);
        crdtTree.apply(clientEditRequest);

        String updatedText = objectMapper.writeValueAsString(crdtTree.getText());

        for (WebSocketSession s : sessions) {
            System.out.println("Sending message to session: " + s.getId());
            s.sendMessage(new TextMessage(updatedText));
        }
    }
} 
