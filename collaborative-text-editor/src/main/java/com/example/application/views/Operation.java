package com.example.application.views;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.io.Serializable;

/**
 * Represents operations that can be performed on the tree CRDT
 */
@JsonDeserialize(builder = Operation.Builder.class)
public class Operation implements Serializable {
    public enum Type {
        INSERT,
        UPDATE,
        DELETE
    }

    private final Type type;
    private final String nodeId;
    private final String parentId;
    private final int position;
    private final String content;
    private final String userId;
    private final long timestamp;

    private Operation(Builder builder) {
        this.type = builder.type;
        this.nodeId = builder.nodeId;
        this.parentId = builder.parentId;
        this.position = builder.position;
        this.content = builder.content;
        this.userId = builder.userId;
        this.timestamp = builder.timestamp;
    }

    // Getters remain the same
    public Type getType() { return type; }
    public String getNodeId() { return nodeId; }
    public String getParentId() { return parentId; }
    public int getPosition() { return position; }
    public String getContent() { return content; }
    public String getUserId() { return userId; }
    public long getTimestamp() { return timestamp; }

    public static class Builder {
        @JsonProperty("type") 
        private Type type;
        
        @JsonProperty("nodeId")
        private String nodeId;
        
        @JsonProperty("parentId")
        private String parentId;
        
        @JsonProperty("position")
        private int position;
        
        @JsonProperty("content")
        private String content;
        
        @JsonProperty("userId")
        private String userId;
        
        @JsonProperty("timestamp")
        private long timestamp;

        @JsonCreator
        public Builder() {}
        
        public Builder type(Type type) {
            this.type = type;
            return this;
        }

        public Builder nodeId(String nodeId) {
            this.nodeId = nodeId;
            return this;
        }

        public Builder parentId(String parentId) {
            this.parentId = parentId;
            return this;
        }

        public Builder position(int position) {
            this.position = position;
            return this;
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Operation build() {
            if (this.type == null) {
                throw new IllegalStateException("Operation type cannot be null");
            }
            
            // Make nodeId optional for UPDATE operations
            if (type == Type.DELETE && nodeId == null) {
                throw new IllegalStateException("Node ID required for DELETE operations");
            }
            
            if (type == Type.INSERT && parentId == null) {
                throw new IllegalStateException("Parent ID required for INSERT operations");
            }
            
            if (this.timestamp == 0) {
                this.timestamp = System.currentTimeMillis();
            }
            
            return new Operation(this);
        }
    }
}