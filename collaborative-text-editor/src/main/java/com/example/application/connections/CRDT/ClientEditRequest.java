package com.example.application.connections.CRDT;

public class ClientEditRequest {
    public enum Type { INSERT, DELETE }

    public Type type;
    public String value;         // INSERT only
    public int position;         // INSERT only
    public long timestamp;       // INSERT and DELETE
    public String targetId;      // INSERT and DELETE
    public String userId;        // both
    public String documentId;    // both
}
