package com.collab.backend;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Enhanced Operation class with additional fields for CRDT operations
 */
@JsonDeserialize(builder = Operation.Builder.class)
public class Operation implements Serializable {

    public enum Type {
        INSERT, UPDATE, DELETE, DELTA
    }
    
    // Delta operation types used when applying fine-grained changes
    public enum DeltaType {
        INSERT, DELETE, RETAIN
    }

    private final Type type;
    private final String nodeId;
    private final String parentId;
    private final int position;
    private final String content;
    private final String userId;
    private final long timestamp;
    private final Map<String, Object> metadata; // Flexible metadata for additional info
    private final Map<String, Long> versionVector; // Version vector for this operation
    
    // Fields for delta operations
    private final DeltaType deltaType;
    private final int startOffset;
    private final int endOffset;

    private Operation(Builder builder) {
        this.type = builder.type;
        this.nodeId = builder.nodeId;
        this.parentId = builder.parentId;
        this.position = builder.position;
        this.content = builder.content;
        this.userId = builder.userId;
        this.timestamp = builder.timestamp;
        this.metadata = builder.metadata;
        this.versionVector = builder.versionVector;
        this.deltaType = builder.deltaType;
        this.startOffset = builder.startOffset;
        this.endOffset = builder.endOffset;
    }

    // Getters (needed by Jackson)
    public Type getType() { return type; }
    public String getNodeId() { return nodeId; }
    public String getParentId() { return parentId; }
    public int getPosition() { return position; }
    public String getContent() { return content; }
    public String getUserId() { return userId; }
    public long getTimestamp() { return timestamp; }
    public Map<String, Object> getMetadata() { return metadata; }
    public Map<String, Long> getVersionVector() { return versionVector; }
    public DeltaType getDeltaType() { return deltaType; }
    public int getStartOffset() { return startOffset; }
    public int getEndOffset() { return endOffset; }

    @JsonPOJOBuilder(withPrefix = "") // no `with` or `set` prefixes
    public static class Builder {
        private Type type;
        private String nodeId;
        private String parentId;
        private int position;
        private String content;
        private String userId;
        private long timestamp;
        private Map<String, Object> metadata = new HashMap<>();
        private Map<String, Long> versionVector = new HashMap<>();
        private DeltaType deltaType;
        private int startOffset;
        private int endOffset;

        public Builder type(Type type) { this.type = type; return this; }
        public Builder nodeId(String nodeId) { this.nodeId = nodeId; return this; }
        public Builder parentId(String parentId) { this.parentId = parentId; return this; }
        public Builder position(int position) { this.position = position; return this; }
        public Builder content(String content) { this.content = content; return this; }
        public Builder userId(String userId) { this.userId = userId; return this; }
        public Builder timestamp(long timestamp) { this.timestamp = timestamp; return this; }
        
        public Builder addMetadata(String key, Object value) { 
            this.metadata.put(key, value); 
            return this; 
        }
        
        public Builder setMetadata(Map<String, Object> metadata) {
            this.metadata = new HashMap<>(metadata);
            return this;
        }
        
        public Builder addVersionVector(String userId, long version) {
            this.versionVector.put(userId, version);
            return this;
        }
        
        public Builder setVersionVector(Map<String, Long> versionVector) {
            this.versionVector = new HashMap<>(versionVector);
            return this;
        }
        
        public Builder deltaType(DeltaType deltaType) {
            this.deltaType = deltaType;
            return this;
        }
        
        public Builder startOffset(int startOffset) {
            this.startOffset = startOffset;
            return this;
        }
        
        public Builder endOffset(int endOffset) {
            this.endOffset = endOffset;
            return this;
        }

        public Operation build() {
            // Default timestamp logic
            if (this.timestamp == 0) this.timestamp = System.currentTimeMillis();
            return new Operation(this);
        }
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Operation{type=").append(type);
        if (nodeId != null) sb.append(", nodeId='").append(nodeId).append('\'');
        if (parentId != null) sb.append(", parentId='").append(parentId).append('\'');
        if (position >= 0) sb.append(", position=").append(position);
        if (content != null) {
            String contentPreview = content.length() > 20 ? 
                content.substring(0, 17) + "..." : content;
            sb.append(", content='").append(contentPreview).append('\'');
        }
        sb.append(", userId='").append(userId).append('\'');
        sb.append(", timestamp=").append(timestamp);
        
        if (deltaType != null) {
            sb.append(", deltaType=").append(deltaType);
            sb.append(", startOffset=").append(startOffset);
            sb.append(", endOffset=").append(endOffset);
        }
        
        sb.append('}');
        return sb.toString();
    }
}