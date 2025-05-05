package com.example.application.connections.CRDT;
public class ClientEditRequest {
    public enum Type { INSERT, DELETE , CURSOR}

    public Type type;       // "INSERT" or "DELETE"
    public String value;      // for INSERT
    public int position;      // for INSERT and DELETE
    public long timestamp;    // for INSERT and DELETE
    public String userId;     // for "INSERT" and "DELETE"
    public String documentId; // for "INSERT" and "DELETE"
}