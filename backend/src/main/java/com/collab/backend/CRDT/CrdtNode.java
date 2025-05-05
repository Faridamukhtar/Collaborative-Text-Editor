package com.collab.backend.crdt;

import java.util.*;

public class CrdtNode {
    public final String id;
    public final String value;
    public final String parentId;
    public final long timestamp;
    public final String userId;
    public boolean isDeleted = false;
    // Initialize with CrdtNodeComparator to ensure consistent ordering
    public final PriorityQueue<CrdtNode> children = new PriorityQueue<>(new CrdtNodeComparator());

    public CrdtNode(String id, String value, String parentId, long timestamp, String userId) {
        this.id = id;
        this.value = value;
        this.parentId = parentId;
        this.timestamp = timestamp;
        this.userId = userId;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        CrdtNode other = (CrdtNode) obj;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}