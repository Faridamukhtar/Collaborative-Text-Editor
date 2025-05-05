package com.collab.backend.crdt;

import java.util.*;

public class CrdtNode {
    public final String id;
    public final String value;
    public final String parentId;
    public final long timestamp;
    public final String userId;
    public boolean isDeleted = false;
    public final PriorityQueue<CrdtNode> children = new PriorityQueue<>(new CrdtNodeComparator());

    public CrdtNode(String id, String value, String parentId, long timestamp, String userId) {
        this.id = id;
        this.value = value;
        this.parentId = parentId;
        this.timestamp = timestamp;
        this.userId = userId;
    }
}