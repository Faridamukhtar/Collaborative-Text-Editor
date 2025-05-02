package com.example.application.connections;

import javax.websocket.Session;
import com.google.gson.JsonObject;

public class ErrorHandler {

    // Handle malformed message errors
    public void handleMalformedMessage(String message) {
        System.err.println("Malformed message: " + message);
    }

    // Handle unknown operation type errors
    public void handleUnknownOperation(String operation) {
        System.err.println("Unknown operation: " + operation);
    }

    // Handle permission denied errors
    public void handlePermissionDenied(String userId) {
        System.err.println("Permission denied for user: " + userId);
    }

    // Send error message back to the WebSocket client
    public void sendError(Session websocketSession, String errorMsg) {
        JsonObject error = new JsonObject();
        error.addProperty("type", "error");
        error.addProperty("message", errorMsg);

        try {
            // Send error message to client using the WebSocket session
            websocketSession.getBasicRemote().sendText(error.toString());
        } catch (Exception e) {
            System.err.println("Failed to send error message: " + e.getMessage());
        }
    }
}
