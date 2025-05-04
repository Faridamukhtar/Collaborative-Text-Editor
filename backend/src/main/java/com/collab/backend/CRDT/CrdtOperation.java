package com.collab.backend.CRDT;

public class CrdtOperation {
    public enum Type { INSERT, DELETE }

    public Type type;
    public String id;
    public String value;
    public String parentId;
    public String targetId;
    public long timestamp;
    public String userId;
    public String documentId;

    // Constructors
    public static CrdtOperation insert(String documentId, String id, String value, String parentId, long timestamp, String userId) {
        CrdtOperation op = new CrdtOperation();
        op.type = Type.INSERT;
        op.documentId = documentId;
        op.id = id;
        op.value = value;
        op.parentId = parentId;
        op.timestamp = timestamp;
        op.userId = userId;
        return op;
    }

    public static CrdtOperation delete(String targetId, long timestamp, String userId, String documentId) {
        CrdtOperation op = new CrdtOperation();
        op.documentId = documentId;
        op.type = Type.DELETE;
        op.targetId = targetId;
        op.timestamp = timestamp;
        op.userId = userId;
        return op;
    }
}