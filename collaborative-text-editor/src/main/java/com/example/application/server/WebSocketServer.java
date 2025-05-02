package com.example.application.server;

import com.example.application.connections.ConnectionManager;
import com.example.application.connections.ErrorHandler;
import com.example.application.connections.ReconnectionManager;
import com.example.application.connections.model.CollaborativeSession;
import com.example.application.connections.model.User;
import com.google.gson.JsonObject;

import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;

@ServerEndpoint("/collaborate")
public class WebSocketServer {

    // Dependencies
    private static ConnectionManager connectionManager = new ConnectionManager();
    private static ErrorHandler errorHandler = new ErrorHandler();
    private static ReconnectionManager reconnectionManager = new ReconnectionManager();

    @OnOpen
    public void onOpen(Session session) {
        System.out.println("New connection opened: " + session.getId());
    }

    @OnMessage
    public void onMessage(String message, javax.websocket.Session websocketSession) {
        try {
            JsonObject jsonMsg = new JsonObject();
            jsonMsg.addProperty("message", message);
            // Assuming the message is a valid JSON containing the type of operation
            String type = jsonMsg.get("type").getAsString();

            if (type.equals("join")) {
                handleJoin(jsonMsg, websocketSession);
            } else if (type.equals("edit")) {
                handleEdit(jsonMsg, websocketSession);
            } else if (type.equals("reconnect")) {
                handleReconnection(jsonMsg, websocketSession);
            } else {
                // Call the sendError method from ErrorHandler
                errorHandler.sendError(websocketSession, "Unknown operation: " + type);  // Send error here
            }

        } catch (Exception e) {
            e.printStackTrace();
            // Send error using sendError from ErrorHandler
            errorHandler.sendError(websocketSession, "Failed to process the message"); // Send error here
        }
    }

    @OnClose
    public void onClose(javax.websocket.Session websocketSession) {
        System.out.println("Connection closed: " + websocketSession.getId());
        String userId = websocketSession.getId();
        // Handle disconnection logic, remove user from active session, or mark them for reconnection
        User user = findUserBySession(websocketSession);
        if (user != null) {
            handleUserDisconnection(user, websocketSession);
        }
    }

    @OnError
    public void onError(javax.websocket.Session websocketSession, Throwable throwable) {
        System.out.println("Error on connection: " + websocketSession.getId() + " - " + throwable.getMessage());
        errorHandler.sendError(websocketSession, "An error occurred during the WebSocket communication");
    }

    private void handleJoin(JsonObject jsonMsg, javax.websocket.Session websocketSession) {
        String userId = jsonMsg.get("userId").getAsString();
        String role = jsonMsg.get("role").getAsString(); // "editor"/"viewer"
        String sessionCode = jsonMsg.get("sessionCode").getAsString();

        CollaborativeSession existingSession = connectionManager.getSession(sessionCode);
        if (existingSession == null) {
            errorHandler.sendError(websocketSession, "Session not found");
            return;
        }

        User user = new User(userId, "User Name", role);
        existingSession.addUser(user);

        // Notify other clients about the new user
        connectionManager.broadcastEdit(sessionCode, "User " + user.getName() + " joined the session");

        System.out.println("User " + user.getName() + " joined session: " + sessionCode);
    }

    private void handleEdit(JsonObject jsonMsg, javax.websocket.Session websocketSession) {
        String sessionCode = jsonMsg.get("sessionCode").getAsString();
        String editOperation = jsonMsg.get("editOperation").getAsString();

        // Broadcast the edit operation to all users in the session
        connectionManager.broadcastEdit(sessionCode, editOperation);

        System.out.println("Edit operation broadcasted: " + editOperation);
    }

    private void handleReconnection(JsonObject jsonMsg, javax.websocket.Session websocketSession) {
        String userId = jsonMsg.get("userId").getAsString();
        User user = findUserById(userId);

        if (user == null) {
            errorHandler.sendError(websocketSession, "User not found for reconnection");
            return;
        }

        // Mark the user as reconnecting
        reconnectionManager.markUserReconnecting(user);
        reconnectionManager.startReconnectionTimer(userId, () -> {
            // Timeout reached, remove the user from the reconnecting state
            reconnectionManager.completeReconnection(userId);
            System.out.println("Reconnection failed for user " + userId);
        });

        // Send missed operations to the reconnecting user
        reconnectionManager.sendMissedOperations(websocketSession, user);
        System.out.println("Missed operations sent to " + user.getName() + " for reconnection");
    }

    private void handleUserDisconnection(User user, javax.websocket.Session websocketSession) {
        CollaborativeSession userSession = connectionManager.getSession(user.getId());
        if (userSession != null) {
            userSession.removeUser(user);
            System.out.println("User " + user.getName() + " has been removed from session.");
        }

        // Add the user to the reconnection manager to handle the reconnection process
        reconnectionManager.markUserReconnecting(user);
        System.out.println("User " + user.getName() + " is marked as reconnecting.");
    }

    private User findUserBySession(javax.websocket.Session websocketSession) {
        // Assuming session can be mapped to a user (in your real-world app, this may be different)
        String userId = websocketSession.getId();
        return findUserById(userId);
    }

    private User findUserById(String userId) {
        // Implement logic to retrieve user from active sessions or storage
        // Placeholder for retrieving the user from your connection/session manager
        return new User(userId, "Example User", "editor");
    }

    public static void main(String[] args) {
        // Start WebSocket server logic if needed, this could be done in your container/server context.
    }
}
