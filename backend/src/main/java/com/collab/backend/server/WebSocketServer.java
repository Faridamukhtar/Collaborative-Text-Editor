package com.collab.backend.server;

import com.collab.backend.connections.ConnectionManager;
import com.collab.backend.connections.ErrorHandler;
import com.collab.backend.connections.ReconnectionManager;
import com.collab.backend.connections.model.CollaborativeSession;
import com.collab.backend.connections.model.User;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebSocketServer extends TextWebSocketHandler {

    private static final ConnectionManager connectionManager = new ConnectionManager();
    private static final ErrorHandler errorHandler = new ErrorHandler();
    private static final ReconnectionManager reconnectionManager = new ReconnectionManager();

    private static final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        System.out.println("New connection opened: " + session.getId());
        sessions.put(session.getId(), session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            JsonObject jsonMsg = JsonParser.parseString(message.getPayload()).getAsJsonObject();
            String type = jsonMsg.get("type").getAsString();

            switch (type) {
                case "join":
                    handleJoin(jsonMsg, session);
                    break;
                case "edit":
                    handleEdit(jsonMsg, session);
                    break;
                case "reconnect":
                    handleReconnection(jsonMsg, session);
                    break;
                default:
                    errorHandler.sendError(session, "Unknown operation: " + type);
            }
        } catch (Exception e) {
            e.printStackTrace();
            errorHandler.sendError(session, "Failed to process the message");
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        System.out.println("Connection closed: " + session.getId());
        User user = findUserBySession(session);
        if (user != null) {
            handleUserDisconnection(user, session);
        }
        sessions.remove(session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        System.out.println("Error on connection: " + session.getId() + " - " + exception.getMessage());
        errorHandler.sendError(session, "An error occurred during the WebSocket communication");
    }

    private void handleJoin(JsonObject jsonMsg, WebSocketSession session) {
        String userId = jsonMsg.get("userId").getAsString();
        String role = jsonMsg.get("role").getAsString();
        String sessionCode = jsonMsg.get("sessionCode").getAsString();

        CollaborativeSession existingSession = connectionManager.getSession(sessionCode);
        if (existingSession == null) {
            errorHandler.sendError(session, "Session not found");
            return;
        }

        User user = new User(userId, "User Name", role);
        existingSession.addUser(user);
        connectionManager.broadcastEdit(sessionCode, "User " + user.getName() + " joined the session");

        System.out.println("User " + user.getName() + " joined session: " + sessionCode);
    }

    private void handleEdit(JsonObject jsonMsg, WebSocketSession session) {
        String sessionCode = jsonMsg.get("sessionCode").getAsString();
        String editOperation = jsonMsg.get("editOperation").getAsString();

        connectionManager.broadcastEdit(sessionCode, editOperation);
        System.out.println("Edit operation broadcasted: " + editOperation);
    }

    private void handleReconnection(JsonObject jsonMsg, WebSocketSession session) {
        String userId = jsonMsg.get("userId").getAsString();
        User user = findUserById(userId);

        if (user == null) {
            errorHandler.sendError(session, "User not found for reconnection");
            return;
        }

        reconnectionManager.markUserReconnecting(user);
        reconnectionManager.startReconnectionTimer(userId, () -> {
            reconnectionManager.completeReconnection(userId);
            System.out.println("Reconnection failed for user " + userId);
        });

        reconnectionManager.sendMissedOperations(session, user);
        System.out.println("Missed operations sent to " + user.getName() + " for reconnection");
    }

    private void handleUserDisconnection(User user, WebSocketSession session) {
        CollaborativeSession userSession = connectionManager.getSession(user.getId());
        if (userSession != null) {
            userSession.removeUser(user);
            System.out.println("User " + user.getName() + " has been removed from session.");
        }

        reconnectionManager.markUserReconnecting(user);
        System.out.println("User " + user.getName() + " is marked as reconnecting.");
    }

    private User findUserBySession(WebSocketSession session) {
        return findUserById(session.getId()); // Or another mechanism that maps session to user
    }

    private User findUserById(String userId) {
        return new User(userId, "Example User", "editor"); // Placeholder, replace with real lookup logic
    }
}
