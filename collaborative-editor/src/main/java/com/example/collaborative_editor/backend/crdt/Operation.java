package com.example.collaborative_editor.backend.crdt;

public class Operation {
    public enum Type {
        INSERT, DELETE
    }

    private final Type type;
    private final CrdtChar character;
    private final String userId;
    private final String sessionId;
    private final long timestamp;

    public Operation(Type type, CrdtChar character, String userId, String sessionId, long timestamp) {
        this.type = type;
        this.character = character;
        this.userId = userId;
        this.sessionId = sessionId;
        this.timestamp = timestamp;
    }

    public Type getType() {
        return type;
    }

    public CrdtChar getCharacter() {
        return character;
    }

    public String getUserId() {
        return userId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public long getTimestamp() {
        return timestamp;
    }
}