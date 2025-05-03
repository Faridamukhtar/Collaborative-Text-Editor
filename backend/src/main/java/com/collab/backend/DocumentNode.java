package com.collab.backend;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a node in the document tree
 */
public class DocumentNode {
    private final String id;
    private String content;
    private final List<DocumentNode> children;
    private DocumentNode parent;
    private final long timestamp;
    private String userId;
    private boolean tombstone;

    public DocumentNode(String content, String userId) {
        this.id = UUID.randomUUID().toString();
        this.content = content;
        this.children = new ArrayList<>();
        this.timestamp = System.currentTimeMillis();
        this.userId = userId;
        this.tombstone = false;
    }

    public String getId() {
        return id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public List<DocumentNode> getChildren() {
        return children;
    }

    public void addChild(DocumentNode child) {
        children.add(child);
        child.setParent(this);
    }

    public void addChildAt(DocumentNode child, int index) {
        if (index >= 0 && index <= children.size()) {
            children.add(index, child);
            child.setParent(this);
        }
    }

    public DocumentNode getParent() {
        return parent;
    }

    public void setParent(DocumentNode parent) {
        this.parent = parent;
    }

    public boolean isTombstone() {
        return tombstone;
    }

    public void markAsDeleted() {
        this.tombstone = true;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getUserId() {
        return userId;
    }
}