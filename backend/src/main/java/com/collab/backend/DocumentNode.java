package com.collab.backend;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Enhanced DocumentNode for CRDT with additional metadata
 */
public class DocumentNode {
    private final String id;
    private String content;
    private final List<DocumentNode> children;
    private DocumentNode parent;
    private long timestamp;
    private String userId;
    private boolean tombstone;
    private String type; // Optional: can be used to define node types (e.g., "paragraph", "heading")

    public DocumentNode(String content, String userId) {
        this.id = UUID.randomUUID().toString();
        this.content = content;
        this.children = new ArrayList<>();
        this.timestamp = System.currentTimeMillis();
        this.userId = userId;
        this.tombstone = false;
        this.type = "text"; // Default type
    }
    
    public DocumentNode(String content, String userId, String type) {
        this(content, userId);
        this.type = type;
    }

    public String getId() {
        return id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
        this.timestamp = System.currentTimeMillis(); // Update timestamp on content change
    }
    
    public void setContentWithTimestamp(String content, long timestamp) {
        this.content = content;
        this.timestamp = timestamp;
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

    public boolean removeChild(DocumentNode child) {
        return children.remove(child);
    }
    
    public DocumentNode removeChildAt(int index) {
        if (index >= 0 && index < children.size()) {
            DocumentNode child = children.remove(index);
            return child;
        }
        return null;
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
        this.timestamp = System.currentTimeMillis();
    }

    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    @Override
    public String toString() {
        return "DocumentNode{" +
                "id='" + id + '\'' +
                ", content='" + (content != null ? content.substring(0, Math.min(content.length(), 20)) + "..." : "null") + '\'' +
                ", children=" + children.size() +
                ", timestamp=" + timestamp +
                ", userId='" + userId + '\'' +
                ", tombstone=" + tombstone +
                ", type='" + type + '\'' +
                '}';
    }
}