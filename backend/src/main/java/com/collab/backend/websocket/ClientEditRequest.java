package com.collab.backend.websocket;

public class ClientEditRequest {
    public enum Type { INSERT, DELETE, ADD_COMMENT, DELETE_COMMENT }

    public Type type;         // "INSERT" or "DELETE" or "ADD_COMMENT" or "DELETE_COMMENT"
    public String value;      // for INSERT
    public int position;      // for INSERT -> start position
    public int endPosition;   // for INSERT -> end position
    public long timestamp;    // for INSERT and DELETE
    public String targetId;   // for "INSERT" and "DELETE"
    public String userId;     // for "INSERT" and "DELETE"
    public String documentId; // for "INSERT" and "DELETE"
    public String commentId;  // for "addComment"

    public Type getType() {
        return type;
    }

    public String getValue() {
        return value;
    }

    public int getPosition() {
        return position;
    }

    public int getEndPosition() {
        return endPosition;
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

    public String getCommentId() {
        return commentId;
    }
}