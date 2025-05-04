package com.collab.backend.websocket;

public class ClientEditRequest {
    public enum Type { INSERT, DELETE }

    public Type type;       // "INSERT" or "DELETE"
    public String value;      // for INSERT
    public int position;      // for INSERT
    public long timestamp;    // for INSERT and DELETE
    public String targetId;   // for "INSERT" and "DELETE"
    public String userId;     // for "INSERT" and "DELETE"
    public String documentId; // for "INSERT" and "DELETE"
}