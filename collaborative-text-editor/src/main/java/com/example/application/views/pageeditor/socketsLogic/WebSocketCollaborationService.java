
package com.example.application.views.pageeditor.socketsLogic;
import com.example.application.views.pageeditor.CRDT.*;

import com.vaadin.flow.shared.Registration;
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
 * WebSocket implementation of CollaborationService
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

    /**
     * Create a new WebSocket collaboration service
     * @param serverUrl The URL of the WebSocket server
     * @param documentId The ID of the document
     * @param userId The ID of the user
     */
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
            String url = String.format("%s/collab/%s?userId=%s", serverUrl, documentId, userId);
            client.doHandshake(new CollaborationHandler(), url);
            logger.info("Connecting to collaboration server: {}", url);
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
    public CompletableFuture<String> requestInitialState() {
        if (isConnected.get()) {
            return initialStateFuture;
        } else {
            CompletableFuture<String> future = new CompletableFuture<>();
            connectionListeners.add(() -> {
                if (isConnected.get()) {
                    future.complete(initialStateFuture.join());
                } else {
                    future.completeExceptionally(new IllegalStateException("Not connected"));
                }
            });
            return future;
        }
    }

    @Override
    public void sendOperation(TextOperation operation) {
        if (session == null || !session.isOpen()) {
            logger.warn("Cannot send operation - no active connection");
            return;
        }

        try {
            String message = "OP:" + OperationSerializer.serialize(operation);
            session.sendMessage(new TextMessage(message));
            logger.debug("Sent operation: {}", operation);
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
    public Registration subscribeToChanges(Consumer<TextOperation> listener) {
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
        }

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            try {
                String payload = message.getPayload();
                if (payload.startsWith("INIT:")) {
                    handleInitialState(payload.substring(5));
                } else if (payload.startsWith("OP:")) {
                    handleOperation(payload.substring(3));
                } else if (payload.equals("PING")) {
                    handlePing();
                }
            } catch (Exception e) {
                logger.error("Error handling message", e);
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

        private void handleInitialState(String content) {
            logger.debug("Received initial state");
            initialStateFuture.complete(content);
        }

        private void handleOperation(String operationJson) {
            try {
                TextOperation operation = OperationSerializer.deserialize(operationJson);
                logger.debug("Received operation: {}", operation);
                notifyOperationReceived(operation);
            } catch (Exception e) {
                logger.error("Failed to deserialize operation", e);
            }
        }

        private void handlePing() {
            try {
                session.sendMessage(new TextMessage("PONG"));
            } catch (IOException e) {
                logger.warn("Failed to send PONG", e);
            }
        }
    }
}