package com.example.application.views;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import elemental.json.Json;
import elemental.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Service
public class WebSocketService {
    private static final Logger log = LoggerFactory.getLogger(WebSocketService.class);

    private static final String WS_URL = "ws://localhost:8081/collaborate/";
    private static final int MAX_RECONNECT_ATTEMPTS = 5;
    private static final int RECONNECT_DELAY_MS = 2000;
    private static final int RETRY_OPERATION_DELAY_MS = 500;
    private static final int MAX_RETRY_ATTEMPTS = 3;

    private final ObjectMapper objectMapper;
    private final Queue<Operation> pendingOperations = new ConcurrentLinkedQueue<>();
    private final ScheduledExecutorService scheduler = new ScheduledThreadPoolExecutor(1);

    private WebSocketConnection socket;
    private String documentId;
    private String userId;
    private int reconnectAttempts = 0;
    private boolean intentionalClose = false;
    private boolean connected = false;
    private long lastConnectionAttempt = 0;

    private Consumer<Operation> operationCallback;
    private Consumer<CursorPosition> cursorCallback;
    private Consumer<String> userJoinedCallback;
    private Consumer<String> userLeftCallback;
    private Consumer<String> errorCallback;
    private Consumer<String> syncCallback;

    public WebSocketService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void setSyncCallback(Consumer<String> syncCallback) {
        this.syncCallback = syncCallback;
    }

    public void setErrorCallback(Consumer<String> errorCallback) {
        this.errorCallback = errorCallback;
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

        // Avoid rapid reconnection attempts
        long now = System.currentTimeMillis();
        if (now - lastConnectionAttempt < RECONNECT_DELAY_MS) {
            log.warn("Throttling connection attempts");
            if (errorCallback != null) {
                errorCallback.accept("Too many connection attempts. Please wait before trying again.");
            }
            return;
        }
        
        lastConnectionAttempt = now;

        if (socket != null && socket.isConnected()) {
            disconnect();
        }

        intentionalClose = false;
        reconnectAttempts = 0;
        pendingOperations.clear();

        connectWebSocket();
    }

    private void connectWebSocket() {
        try {
            String wsUrl = WS_URL + documentId;
            log.info("Connecting to WebSocket at {}", wsUrl);
            socket = new WebSocketConnection(wsUrl);

            socket.setOnOpen(() -> {
                log.info("Connected to WebSocket for document {}, user {}", documentId, userId);
                connected = true;
                reconnectAttempts = 0;
                sendPresence(true);

                // Request initial document state
                JsonObject message = Json.createObject();
                message.put("type", "REQUEST_SYNC");
                message.put("documentId", documentId);
                socket.sendMessage(message.toJson());
                
                // Process any pending operations
                processPendingOperations();
            });

            socket.setOnMessage(this::handleIncomingMessage);

            socket.setOnClose(() -> {
                log.info("WebSocket closed for document {}", documentId);
                connected = false;

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
                    errorCallback.accept("Failed to reconnect to WebSocket server after " + 
                                         MAX_RECONNECT_ATTEMPTS + " attempts");
                }
            });

            socket.setOnError(error -> {
                log.error("WebSocket error: {}", error);
                connected = false;
                if (errorCallback != null) {
                    errorCallback.accept("WebSocket error: " + error);
                }
            });

            socket.connect();
        } catch (Exception e) {
            connected = false;
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
            connected = false;
            log.info("Disconnected WebSocket for user {}, document {}", userId, documentId);
        }
    }

    public void sendOperation(Operation operation) {
        if (!connected) {
            log.warn("WebSocket not connected, queuing operation");
            pendingOperations.offer(operation);
            return;
        }

        try {
            String operationJson = objectMapper.writeValueAsString(operation);
        
            JsonObject message = Json.createObject();
            message.put("type", "OPERATION");
            message.put("documentId", documentId);
            message.put("userId", userId);
            message.put("operation", Json.parse(operationJson)); // parsed into nested JSON
        
            log.info("Sending OPERATION for document={}, user={}", documentId, userId);
            
            // Send with retry logic
            sendWithRetry(message.toJson(), operation);
        
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize operation", e);
            if (errorCallback != null) {
                errorCallback.accept("Failed to send operation: " + e.getMessage());
            }
        }
    }
    
    private void sendWithRetry(String message, Operation operation) {
        try {
            socket.sendMessage(message);
        } catch (Exception e) {
            log.error("Failed to send message, will retry: {}", e.getMessage());
            
            // Add to pending operations and schedule retry
            pendingOperations.offer(operation);
            
            // Try to reconnect if needed
            if (!connected) {
                reconnect();
            }
        }
    }
    
    private void reconnect() {
        if (System.currentTimeMillis() - lastConnectionAttempt < RECONNECT_DELAY_MS) {
            log.info("Too soon to reconnect, will try later");
            return;
        }
        
        log.info("Attempting to reconnect...");
        connectWebSocket();
    }

    private void processPendingOperations() {
        if (pendingOperations.isEmpty()) {
            return;
        }
        
        log.info("Processing {} pending operations", pendingOperations.size());
        
        // Create a copy to avoid concurrent modification
        Operation[] operations = pendingOperations.toArray(new Operation[0]);
        pendingOperations.clear();
        
        // Process operations in order
        for (Operation op : operations) {
            sendOperation(op);
        }
    }

    public void sendCursorPosition(int position) {
        if (!connected) {
            return;
        }

        JsonObject cursorPosition = Json.createObject();
        cursorPosition.put("userId", userId);
        cursorPosition.put("position", position);

        JsonObject message = Json.createObject();
        message.put("type", "CURSOR_MOVE");
        message.put("documentId", documentId);
        message.put("cursorPosition", cursorPosition);

        socket.sendMessage(message.toJson());
    }
    
    /**
     * Send user presence information to the server
     * @param present true if user is joining, false if leaving
     */
    private void sendPresence(boolean present) {
        if (!connected) {
            return;
        }
        
        JsonObject message = Json.createObject();
        message.put("type", present ? "USER_JOINED" : "USER_LEFT");
        message.put("documentId", documentId);
        message.put("userId", userId);
        
        try {
            socket.sendMessage(message.toJson());
            log.info("Sent presence update: user {} is {}", userId, present ? "online" : "offline");
        } catch (Exception e) {
            log.error("Failed to send presence update", e);
        }
    }
    
    /**
     * Handle incoming WebSocket messages
     */
    private void handleIncomingMessage(String messageJson) {
        try {
            JsonNode message = objectMapper.readTree(messageJson);
            String type = message.get("type").asText();
            
            log.info("Received message type: {}", type);
            
            switch (type) {
                case "OPERATION":
                    handleOperation(message);
                    break;
                case "CURSOR_MOVE":
                    handleCursorMove(message);
                    break;
                case "USER_JOINED":
                    handleUserJoined(message);
                    break;
                case "USER_LEFT":
                    handleUserLeft(message);
                    break;
                case "SYNC":
                    handleSync(message);
                    break;
                case "ERROR":
                    handleErrorMessage(message);
                    break;
                default:
                    log.warn("Unknown message type: {}", type);
            }
        } catch (Exception e) {
            log.error("Error handling WebSocket message: {}", e.getMessage());
            log.debug("Message content: {}", messageJson);
        }
    }
    
    private void handleOperation(JsonNode message) throws JsonProcessingException {
        if (operationCallback == null) {
            return;
        }
        
        JsonNode operationNode = message.get("operation");
        Operation operation = objectMapper.treeToValue(operationNode, Operation.class);
        
        log.info("Received operation from user {}: {}", 
                 operation.getUserId(), operation.getType());
        
        operationCallback.accept(operation);
    }
    
    private void handleCursorMove(JsonNode message) {
        if (cursorCallback == null) {
            return;
        }
        
        JsonNode cursorNode = message.get("cursorPosition");
        String cursorUserId = cursorNode.get("userId").asText();
        int position = cursorNode.get("position").asInt();
        
        // Skip our own cursor movements
        if (cursorUserId.equals(userId)) {
            return;
        }
        
        CursorPosition cursorPosition = new CursorPosition(cursorUserId, position);
        cursorCallback.accept(cursorPosition);
    }
    
    private void handleUserJoined(JsonNode message) {
        if (userJoinedCallback == null) {
            return;
        }
        
        String joinedUserId = message.get("userId").asText();
        log.info("User joined: {}", joinedUserId);
        userJoinedCallback.accept(joinedUserId);
    }
    
    private void handleUserLeft(JsonNode message) {
        if (userLeftCallback == null) {
            return;
        }
        
        String leftUserId = message.get("userId").asText();
        log.info("User left: {}", leftUserId);
        userLeftCallback.accept(leftUserId);
    }
    
    private void handleSync(JsonNode message) {
        if (syncCallback == null) {
            return;
        }
        
        String content = message.get("content").asText();
        log.info("Received sync with content length: {}", content.length());
        syncCallback.accept(content);
    }
    
    private void handleErrorMessage(JsonNode message) {
        if (errorCallback == null) {
            return;
        }
        
        String errorMsg = message.get("message").asText();
        log.warn("Received error from server: {}", errorMsg);
        errorCallback.accept(errorMsg);
    }
    
    /**
     * Class representing a cursor position for a specific user
     */
    public static class CursorPosition {
        private final String userId;
        private final int position;
        
        public CursorPosition(String userId, int position) {
            this.userId = userId;
            this.position = position;
        }
        
        public String getUserId() {
            return userId;
        }
        
        public int getPosition() {
            return position;
        }
        
        @Override
        public String toString() {
            return "CursorPosition{userId='" + userId + "', position=" + position + '}';
        }
    }
}