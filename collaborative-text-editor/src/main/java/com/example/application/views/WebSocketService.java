// ✅ FIXED VERSION of WebSocketService.java
package com.example.application.views;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import elemental.json.Json;
import elemental.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

@Service
public class WebSocketService {
    private static final Logger log = LoggerFactory.getLogger(WebSocketService.class);

    private static final String WS_URL = "ws://localhost:8081/collaborate/";
    private static final int MAX_RECONNECT_ATTEMPTS = 5;
    private static final int RECONNECT_DELAY_MS = 2000;

    private final ObjectMapper objectMapper;

    private WebSocketConnection socket;
    private String documentId;
    private String userId;
    private int reconnectAttempts = 0;
    private boolean intentionalClose = false;

    private Consumer<Operation> operationCallback;
    private Consumer<CursorPosition> cursorCallback;
    private Consumer<String> userJoinedCallback;
    private Consumer<String> userLeftCallback;
    private Consumer<String> errorCallback;
    private Consumer<String> documentLoadCallback;
    private Consumer<String> syncCallback;
    public void setSyncCallback(Consumer<String> syncCallback) {
        this.syncCallback = syncCallback;
    }

    public WebSocketService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void setDocumentLoadCallback(Consumer<String> callback) {
        this.documentLoadCallback = callback;
    }

    public void connect(String documentId, String userId,
                        Consumer<Operation> operationCallback,
                        Consumer<Object> cursorCallback,
                        Consumer<String> userJoinedCallback,
                        Consumer<String> userLeftCallback) {

        this.documentId = documentId;
        this.userId = userId;
        this.operationCallback = operationCallback;
        this.cursorCallback = position -> cursorCallback.accept(position);
        this.userJoinedCallback = userJoinedCallback;
        this.userLeftCallback = userLeftCallback;

        if (socket != null && socket.isConnected()) {
            disconnect();
        }

        intentionalClose = false;
        reconnectAttempts = 0;

        connectWebSocket();
    }

    private void connectWebSocket() {
        try {
            String wsUrl = WS_URL + documentId;
            socket = new WebSocketConnection(wsUrl);

            socket.setOnOpen(() -> {
                log.info("Connected to WebSocket for document {}, user {}", documentId, userId);
                reconnectAttempts = 0;
                sendPresence(true);

                JsonObject message = Json.createObject();
                message.put("type", "REQUEST_SYNC");
                message.put("documentId", documentId);
                socket.sendMessage(message.toJson());
            });

            socket.setOnMessage(this::handleIncomingMessage);

            socket.setOnClose(() -> {
                log.info("WebSocket closed for document {}", documentId);

                if (!intentionalClose && reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                    reconnectAttempts++;
                    log.info("Reconnecting... attempt {}", reconnectAttempts);
                    try {
                        Thread.sleep(RECONNECT_DELAY_MS);
                        connectWebSocket();
                    } catch (InterruptedException e) {
                        log.error("Reconnect interrupted", e);
                        Thread.currentThread().interrupt();
                    }
                } else if (!intentionalClose && errorCallback != null) {
                    errorCallback.accept("Failed to reconnect to WebSocket server");
                }
            });

            socket.setOnError(error -> {
                log.error("WebSocket error: {}", error);
                if (errorCallback != null) {
                    errorCallback.accept("WebSocket error: " + error);
                }
            });

            socket.connect();
        } catch (Exception e) {
            log.error("Failed to connect WebSocket", e);
            if (errorCallback != null) {
                errorCallback.accept("Connection error: " + e.getMessage());
            }
        }
    }

    public void disconnect() {
        if (socket != null && socket.isConnected()) {
            intentionalClose = true;
            sendPresence(false);
            socket.disconnect();
            log.info("Disconnected WebSocket for user {}, document {}", userId, documentId);
        }
    }

    public void sendOperation(Operation operation) {
        if (socket == null || !socket.isConnected()) {
            log.warn("Cannot send operation - WebSocket not connected");
            return;
        }

        try {
            String operationJson = objectMapper.writeValueAsString(operation);
        
            JsonObject message = Json.createObject();
            message.put("type", "OPERATION");
            message.put("documentId", documentId);
            message.put("userId", userId);
            message.put("operation", Json.parse(operationJson)); // parsed into nested JSON
        
            log.info("[WEBSOCKET][SEND] Sending OPERATION for document={}, user={}", documentId, userId);
            log.info("[WEBSOCKET][SEND] Payload: {}", message.toJson());
        
            socket.sendMessage(message.toJson());
        
        } catch (JsonProcessingException e) {
            log.error("[WEBSOCKET][ERROR] Failed to serialize operation for document={}, user={}", documentId, userId, e);
        }
    }

    public void sendCursorPosition(String documentId, String userId, int offset) {
        if (socket == null || !socket.isConnected()) {
            log.warn("Cannot send cursor position - WebSocket not connected");
            return;
        }

        String nodeId = "root";

        JsonObject cursorPosition = Json.createObject();
        cursorPosition.put("userId", userId);
        cursorPosition.put("nodeId", nodeId);
        cursorPosition.put("offset", offset);

        JsonObject message = Json.createObject();
        message.put("type", "CURSOR_MOVE");
        message.put("documentId", documentId);
        message.put("cursorPosition", cursorPosition);

        socket.sendMessage(message.toJson());
    }

    private void sendPresence(boolean online) {
        JsonObject presence = Json.createObject();
        presence.put("userId", userId);
        presence.put("online", online);

        JsonObject message = Json.createObject();
        message.put("type", "USER_PRESENCE");
        message.put("userPresence", presence);

        socket.sendMessage(message.toJson());
    }

    private void handleIncomingMessage(String message) {
        try {
            JsonObject jsonMessage = Json.parse(message);
            String type = jsonMessage.getString("type");

            switch (type) {
                case "OPERATION" -> {
                    if (operationCallback != null && jsonMessage.hasKey("operation")) {
                        Operation operation = parseOperation(jsonMessage.getObject("operation"));
                        if (operation != null && !operation.getUserId().equals(userId)) {
                            operationCallback.accept(operation);
                        }
                    }
                }
                case "CURSOR_MOVE" -> {
                    if (cursorCallback != null && jsonMessage.hasKey("cursorPosition")) {
                        CursorPosition cursorPosition = parseCursorPosition(jsonMessage.getObject("cursorPosition"));
                        if (cursorPosition != null && !cursorPosition.getUserId().equals(userId)) {
                            cursorCallback.accept(cursorPosition);
                        }
                    }
                }
                case "USER_PRESENCE" -> {
                    if (jsonMessage.hasKey("userPresence")) {
                        JsonObject userPresence = jsonMessage.getObject("userPresence");
                        String presenceUserId = userPresence.getString("userId");

                        if (!presenceUserId.equals(userId)) {
                            boolean online = userPresence.getBoolean("online");
                            if (online && userJoinedCallback != null) {
                                userJoinedCallback.accept(presenceUserId);
                            } else if (!online && userLeftCallback != null) {
                                userLeftCallback.accept(presenceUserId);
                            }
                        }
                    }
                }
                case "SYNC_RESPONSE" -> {
                    if (jsonMessage.hasKey("document")) {
                        JsonObject doc = jsonMessage.getObject("document");
                        String html = doc.getString("content"); // assuming backend sends "content"
                        if (syncCallback != null) {
                            syncCallback.accept(html);
                        }
                    }
                    break;
                }
                case "ERROR" -> {
                    if (jsonMessage.hasKey("message")) {
                        String errorMessage = jsonMessage.getString("message");
                        log.error("Error from server: {}", errorMessage);
                        if (errorCallback != null) {
                            errorCallback.accept(errorMessage);
                        }
                    }
                }
                default -> log.warn("Unknown message type: {}", type);
            }
        } catch (Exception e) {
            log.error("Error processing WebSocket message", e);
        }
    }

    private Operation parseOperation(JsonObject operationJson) {
        try {
            String typeStr = operationJson.getString("type");
            Operation.Type operationType = Operation.Type.valueOf(typeStr);

            Operation.Builder builder = new Operation.Builder().type(operationType);

            if (operationJson.hasKey("nodeId")) builder.nodeId(operationJson.getString("nodeId"));
            if (operationJson.hasKey("parentId")) builder.parentId(operationJson.getString("parentId"));
            if (operationJson.hasKey("position")) builder.position((int) operationJson.getNumber("position"));
            if (operationJson.hasKey("content")) builder.content(operationJson.getString("content"));
            if (operationJson.hasKey("userId")) builder.userId(operationJson.getString("userId"));
            if (operationJson.hasKey("timestamp")) builder.timestamp(Math.round(operationJson.getNumber("timestamp")));

            return builder.build();
        } catch (Exception e) {
            log.error("Error parsing operation", e);
            return null;
        }
    }

    private CursorPosition parseCursorPosition(JsonObject cursorJson) {
        try {
            CursorPosition position = new CursorPosition();

            if (cursorJson.hasKey("userId")) position.setUserId(cursorJson.getString("userId"));
            if (cursorJson.hasKey("nodeId")) position.setNodeId(cursorJson.getString("nodeId"));
            if (cursorJson.hasKey("offset")) position.setOffset((int) cursorJson.getNumber("offset"));

            return position;
        } catch (Exception e) {
            log.error("Error parsing cursor position", e);
            return null;
        }
    }

    public void setErrorCallback(Consumer<String> errorCallback) {
        this.errorCallback = errorCallback;
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
}