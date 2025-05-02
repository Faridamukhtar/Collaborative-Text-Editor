package com.example.application.connections.model;

public class User {
    private String id;
    private String name;
    private String role;  // "editor" or "viewer"
    private String lastClock;  // Timestamp for CRDT operations (optional)
    private String reconnectToken;  // For reconnection purposes (optional)

    public User(String id, String name, String role) {
        this.id = id;
        this.name = name;
        this.role = role;
        this.lastClock = "";  // Default value for the clock
        this.reconnectToken = "";  // Can be set during reconnection
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getLastClock() { return lastClock; }
    public void setLastClock(String lastClock) { this.lastClock = lastClock; }

    public String getReconnectToken() { return reconnectToken; }
    public void setReconnectToken(String reconnectToken) { this.reconnectToken = reconnectToken; }
}
