package com.example.application.views.pageeditor.socketsLogic;

import com.example.application.views.pageeditor.CRDT.TextOperation;
import com.vaadin.flow.shared.Registration;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.*;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * WebSocket implementation of CollaborationService, adapted for Spring Boot backend.
 */
public class WebSocketCollaborationService implements CollaborationService {
    private static final Logger logger = LoggerFactory.getLogger(WebSocketCollaborationService.class);
    private static final int RECONNECT_DELAY_MS = 5000;
    private static final int MAX_RETRIES = 5;

    private WebSocketSession session;
    private final WebSocketClient client;
    private final List<Consumer<TextOperation>> operationListeners = new CopyOnWriteArrayList<>();
    private final List<Runnable> connectionListeners = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final String serverUrl;
    private final String documentId;
    private final String userId;
    private final CompletableFuture<String> initialStateFuture = new CompletableFuture<>();
    private final AtomicBoolean isConnected = new AtomicBoolean(false);
    private int retryCount = 0;

    public WebSocketCollaborationService(String serverUrl, String documentId, String userId) {
        this.serverUrl = serverUrl;
        this.documentId = documentId;
        this.userId = userId;
        this.client = new StandardWebSocketClient();
    }

    @Override
    public synchronized void connect() {
        if (isConnected.get()) return;

        try {
            client.doHandshake(new CollaborationHandler(), serverUrl + "/collaborate");
            logger.info("Connecting to collaboration server: {}", serverUrl + "/collaborate");
        } catch (Exception e) {
            logger.error("Connection failed", e);
            scheduleReconnect();
        }
    }

    @Override
    public synchronized void disconnect() {
        if (session != null) {
            try {
                session.close(CloseStatus.NORMAL);
            } catch (IOException e) {
                logger.warn("Error closing session", e);
            }
        }
        executor.shutdown();
        isConnected.set(false);
        notifyConnectionChanged();
    }

    @Override
    public CompletableFuture<String> getInitialContent() {
        // Return empty or wait for initialStateFuture if you plan to implement INIT logic on server
        return CompletableFuture.completedFuture("");
    }

    @Override
    public void sendOperation(TextOperation operation) {
        if (session == null || !session.isOpen()) {
            logger.warn("Cannot send operation - no active connection");
            return;
        }

        try {
            JsonObject msg = new JsonObject();
            msg.addProperty("type", "edit");
            msg.addProperty("sessionCode", documentId);
            msg.addProperty("editOperation", OperationSerializer.serialize(operation));
            session.sendMessage(new TextMessage(msg.toString()));
            logger.debug("Sent edit operation");
        } catch (IOException e) {
            logger.error("Failed to send operation", e);
            handleConnectionError();
        }
    }

    @Override
    public boolean isConnected() {
        return isConnected.get();
    }

    @Override
    public Registration subscribeToConnectionChanges(Runnable listener) {
        connectionListeners.add(listener);
        return () -> connectionListeners.remove(listener);
    }

    @Override
    public Registration subscribeToOperations(Consumer<TextOperation> listener) {
        operationListeners.add(listener);
        return () -> operationListeners.remove(listener);
    }

    private void scheduleReconnect() {
        if (retryCount >= MAX_RETRIES) {
            logger.error("Max reconnection attempts reached");
            return;
        }

        retryCount++;
        logger.info("Scheduling reconnection attempt {} in {}ms", retryCount, RECONNECT_DELAY_MS);
        executor.schedule(this::connect, RECONNECT_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    private void handleConnectionError() {
        isConnected.set(false);
        session = null;
        notifyConnectionChanged();
        scheduleReconnect();
    }

    private void notifyOperationReceived(TextOperation operation) {
        operationListeners.forEach(listener -> {
            try {
                listener.accept(operation);
            } catch (Exception e) {
                logger.error("Error in operation listener", e);
            }
        });
    }

    private void notifyConnectionChanged() {
        connectionListeners.forEach(listener -> {
            try {
                listener.run();
            } catch (Exception e) {
                logger.error("Error in connection listener", e);
            }
        });
    }

    private class CollaborationHandler extends TextWebSocketHandler {
        @Override
        public void afterConnectionEstablished(WebSocketSession session) {
            WebSocketCollaborationService.this.session = session;
            isConnected.set(true);
            retryCount = 0;
            notifyConnectionChanged();
            logger.info("Connected to collaboration server");

            // Immediately send join message
            JsonObject joinMsg = new JsonObject();
            joinMsg.addProperty("type", "join");
            joinMsg.addProperty("userId", userId);
            joinMsg.addProperty("role", "editor");
            joinMsg.addProperty("sessionCode", documentId);

            try {
                session.sendMessage(new TextMessage(joinMsg.toString()));
                logger.debug("Sent join message");
            } catch (IOException e) {
                logger.error("Failed to send join message", e);
            }
        }

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            try {
                String payload = message.getPayload();
                JsonObject msg = JsonParser.parseString(payload).getAsJsonObject();

                String type = msg.get("type").getAsString();
                if (type.equals("edit")) {
                    String opJson = msg.get("editOperation").getAsString();
                    TextOperation operation = OperationSerializer.deserialize(opJson);
                    notifyOperationReceived(operation);
                } else {
                    logger.warn("Unknown message type: {}", type);
                }
            } catch (Exception e) {
                logger.error("Error handling incoming message", e);
            }
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
            logger.info("Connection closed: {}", status);
            handleConnectionError();
        }

        @Override
        public void handleTransportError(WebSocketSession session, Throwable exception) {
            logger.error("Transport error", exception);
            handleConnectionError();
        }
    }
}
