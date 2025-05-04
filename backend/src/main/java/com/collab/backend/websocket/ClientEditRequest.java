package com.collab.backend.websocket;

public class ClientEditRequest {
    public enum Type { INSERT, DELETE }

    public Type type;         // "INSERT" or "DELETE"
    public String value;      // for INSERT
    public int position;      // for INSERT
    public long timestamp;    // for INSERT and DELETE
    public String targetId;   // for "INSERT" and "DELETE"
    public String userId;     // for "INSERT" and "DELETE"
    public String documentId; // for "INSERT" and "DELETE"

    public Type getType() {
        return type;
    }

    public String getValue() {
        return value;
    }

    public int getPosition() {
        return position;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getTargetId() {
        return targetId;
    }

    public String getUserId() {
        return userId;
    }

    public String getDocumentId() {
        return documentId;
    }
}
