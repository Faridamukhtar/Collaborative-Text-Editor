package com.example.application.connections.CRDT;

import java.time.Instant;

public class CollaborativeEditService {

    private final String documentId;

    public CollaborativeEditService(String documentId) {
        this.documentId = documentId;
    }

    public ClientEditRequest createInsertRequest(String character, int position, String userId) {
        long timestamp = Instant.now().toEpochMilli();
        String id = userId + ":" + timestamp;

        ClientEditRequest req = new ClientEditRequest();
        req.type = ClientEditRequest.Type.INSERT;
        req.documentId = documentId;
        req.timestamp = timestamp;
        req.userId = userId;
        req.value = character;
        req.position = position;
        req.targetId = id; // Used to identify the character in CRDT
        return req;
    }

    public ClientEditRequest createDeleteRequest(int position, String userId) {
        long timestamp = Instant.now().toEpochMilli();

        ClientEditRequest req = new ClientEditRequest();
        req.type = ClientEditRequest.Type.DELETE;
        req.documentId = documentId;
        req.timestamp = timestamp;
        req.userId = userId;
        req.position = position;
        req.targetId = null; // will be resolved in backend using visible list
        return req;
    }
}
