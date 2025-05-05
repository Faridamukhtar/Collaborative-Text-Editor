package com.collab.backend.crdt;

import java.util.List;
import com.collab.backend.websocket.ClientEditRequest;

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

    public static CrdtOperation fromClientInsert(ClientEditRequest req, List<String> visibleIds) {
        String targetId = req.userId + "-" + req.timestamp;
        
        String parentId = (req.position <= 0 || visibleIds.isEmpty()) ? "root" : visibleIds.get(Math.min(req.position - 1, visibleIds.size() - 1));

        return insert(
            req.documentId,
            targetId,
            req.value,
            parentId,
            req.timestamp,
            req.userId
        );
    }

    public static CrdtOperation fromClientDelete(ClientEditRequest req, List<String> visibleIds) {   
        String targetId = "";
        if (req.position >= 0 && req.position < visibleIds.size()) {
            targetId = visibleIds.get(req.position);
        }

        return delete(
            targetId,
            req.timestamp,
            req.userId,
            req.documentId
        );
    }
} 