package com.collab.backend.crdt;

import java.util.List;
import com.collab.backend.websocket.ClientEditRequest;

public class CrdtOperation {
    public enum Type { INSERT, DELETE , CURSOR }

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
        
        // Fix parent ID selection for mid-sentence insertions
        String parentId;
        if (req.position <= 0) {
            parentId = "root";
        } else if (req.position >= visibleIds.size()) {
            // If inserting after the last character, use the last character as parent
            parentId = visibleIds.get(visibleIds.size() - 1);
        } else {
            // For mid-sentence insertions, use the character before the insertion point as parent
            parentId = visibleIds.get(req.position - 1);
        }
        
        System.err.println("Parent ID: " + parentId);

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
        
        // Debug log the state
        System.err.println("Delete request at position: " + req.position + 
                          ", visibleIds size: " + visibleIds.size());
        
        // Ensure position is within bounds
        if (req.position >= 0 && req.position < visibleIds.size()) {
            targetId = visibleIds.get(req.position);
            System.err.println("Target ID for deletion: " + targetId);
        } else {
            System.err.println("WARNING: Delete position out of bounds: " + req.position);
        }

        return delete(
            targetId,
            req.timestamp,
            req.userId,
            req.documentId
        );
    }
}