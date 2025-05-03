package com.collab.backend.connections;

import com.google.gson.JsonObject;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

public class ErrorHandler {

    public void handleMalformedMessage(String message) {
        System.err.println("Malformed message: " + message);
    }

    public void handleUnknownOperation(String operation) {
        System.err.println("Unknown operation: " + operation);
    }

    public void handlePermissionDenied(String userId) {
        System.err.println("Permission denied for user: " + userId);
    }

    public void sendError(WebSocketSession websocketSession, String errorMsg) {
        JsonObject error = new JsonObject();
        error.addProperty("type", "error");
        error.addProperty("message", errorMsg);

        try {
            websocketSession.sendMessage(new TextMessage(error.toString()));
        } catch (Exception e) {
            System.err.println("Failed to send error message: " + e.getMessage());
        }
    }
}
