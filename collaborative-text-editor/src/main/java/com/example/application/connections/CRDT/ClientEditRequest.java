package com.example.application.connections.CRDT;
public class ClientEditRequest {

    public enum Type { INSERT, DELETE, ADD_COMMENT, DELETE_COMMENT , CURSOR}


    public Type type;       // "INSERT" or "DELETE"
    public String value;      // for INSERT
    public int position;      // for INSERT and DELETE -> start position
    public int endPosition;   // for Comment -> end position
    public long timestamp;    // for INSERT and DELETE
    public String userId;     // for "INSERT" and "DELETE"
    public String documentId; // for "INSERT" and "DELETE"
    public String commentId;  // for "addComment"
}