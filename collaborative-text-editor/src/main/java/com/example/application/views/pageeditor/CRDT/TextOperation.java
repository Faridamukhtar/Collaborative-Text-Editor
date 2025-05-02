package com.example.application.views.pageeditor.CRDT;

import java.io.Serializable;
import java.util.UUID;

/**
 * Represents an atomic text operation in the collaborative editor.
 * Operations can be of type INSERT or DELETE.
 */
public class TextOperation implements Serializable {
    private static final long serialVersionUID = 1L;
    
    public enum OperationType {
        INSERT,
        DELETE
    }
    
    private final String id;
    private final OperationType type;
    private final Character character;
    private final String positionId;
    private final long timestamp;
    private final String siteId;
    private final String beforePos;
    private final String afterPos;

    private TextOperation(
            String id,
            OperationType type, 
            Character character, 
            String positionId, 
            long timestamp, 
            String siteId,
            String beforePos,
            String afterPos) {
        this.id = id;
        this.type = type;
        this.character = character;
        this.positionId = positionId;
        this.timestamp = timestamp;
        this.siteId = siteId;
        this.beforePos = beforePos;
        this.afterPos = afterPos;
    }

    /**
     * Create an INSERT operation
     */
    public static TextOperation createInsert(
            char character, 
            String positionId, 
            long timestamp, 
            String siteId,
            String beforePos,
            String afterPos) {
        
        return new TextOperation(
                UUID.randomUUID().toString(),
                OperationType.INSERT, 
                character, 
                positionId, 
                timestamp, 
                siteId,
                beforePos,
                afterPos);
    }

    /**
     * Create a DELETE operation
     */
    public static TextOperation createDelete(
            String positionId, 
            long timestamp, 
            String siteId) {
        
        return new TextOperation(
                UUID.randomUUID().toString(),
                OperationType.DELETE, 
                null, 
                positionId, 
                timestamp, 
                siteId,
                null,
                null);
    }

    // Getters
    public String getId() { return id; }
    public OperationType getType() { return type; }
    public Character getCharacter() { return character; }
    public String getPositionId() { return positionId; }
    public long getTimestamp() { return timestamp; }
    public String getSiteId() { return siteId; }
    public String getBeforePos() { return beforePos; }
    public String getAfterPos() { return afterPos; }

    @Override
    public String toString() {
        return "TextOperation{" +
                "id='" + id + '\'' +
                ", type=" + type +
                ", character=" + character +
                ", positionId='" + positionId + '\'' +
                ", timestamp=" + timestamp +
                ", siteId='" + siteId + '\'' +
                ", beforePos='" + beforePos + '\'' +
                ", afterPos='" + afterPos + '\'' +
                '}';
    }
}