package com.example.application.views;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.function.Consumer;

public class WebSocketConnection {
    private static final Logger log = LoggerFactory.getLogger(WebSocketConnection.class);

    private final String url;
    private WebSocketClient client;
    private boolean connected = false;

    private Runnable onOpen;
    private Consumer<String> onMessage;
    private Runnable onClose;
    private Consumer<String> onError;

    public WebSocketConnection(String url) {
        this.url = url;
    }

    public void connect() {
        try {
            client = new WebSocketClient(new URI(url)) {
                @Override
                public void onOpen(ServerHandshake handshakeData) {
                    connected = true;
                    log.info("WebSocket connected to {}", url);
                    if (onOpen != null) onOpen.run();
                }

                @Override
                public void onMessage(String message) {
                    if (onMessage != null) onMessage.accept(message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    connected = false;
                    log.info("WebSocket closed: {}", reason);
                    if (onClose != null) onClose.run();
                }

                @Override
                public void onError(Exception ex) {
                    log.error("WebSocket error: {}", ex.getMessage());
                    if (onError != null) onError.accept(ex.getMessage());
                }
            };
            client.connect();
        } catch (Exception e) {
            log.error("Failed to connect to WebSocket", e);
            if (onError != null) {
                onError.accept("Connection error: " + e.getMessage());
            }
        }
    }

    public void disconnect() {
        if (client != null && client.isOpen()) {
            client.close();
        }
    }

    public void sendMessage(String message) {
        if (client != null && client.isOpen()) {
            log.info("Sending message: {}", message);
            log.info("Sending to client: {}", client);
            client.send(message);
        } else {
            log.warn("WebSocket is not connected. Message not sent.");
        }
    }

    public void setOnOpen(Runnable onOpen) {
        this.onOpen = onOpen;
    }

    public void setOnMessage(Consumer<String> onMessage) {
        this.onMessage = onMessage;
    }

    public void setOnClose(Runnable onClose) {
        this.onClose = onClose;
    }

    public void setOnError(Consumer<String> onError) {
        this.onError = onError;
    }

    public boolean isConnected() {
        return connected;
    }
}