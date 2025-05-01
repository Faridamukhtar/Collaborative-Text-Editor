package com.example.application.views.pageeditor.CRDT;

import java.util.Objects;

public class TextOperation {
    public enum Type { INSERT, DELETE }
    
    private final Type type;
    private final String positionId;
    private final char character;
    private final long timestamp;
    private final String siteId;
    private final String beforePos;
    private final String afterPos;

    public TextOperation(Type type, String positionId, char character,
                        long timestamp, String siteId,
                        String beforePos, String afterPos) {
        this.type = type;
        this.positionId = positionId;
        this.character = character;
        this.timestamp = timestamp;
        this.siteId = siteId;
        this.beforePos = beforePos;
        this.afterPos = afterPos;
    }

    // Factory methods
    public static TextOperation createInsert(char c, String positionId,
                                          long timestamp, String siteId,
                                          String beforePos, String afterPos) {
        return new TextOperation(Type.INSERT, positionId, c, timestamp,
                               siteId, beforePos, afterPos);
    }

    public static TextOperation createDelete(String positionId,
                                          long timestamp, String siteId) {
        return new TextOperation(Type.DELETE, positionId, '\0', timestamp,
                               siteId, null, null);
    }

    // Getters
    public Type getType() { return type; }
    public String getPositionId() { return positionId; }
    public char getCharacter() { return character; }
    public long getTimestamp() { return timestamp; }
    public String getSiteId() { return siteId; }
    public String getBeforePos() { return beforePos; }
    public String getAfterPos() { return afterPos; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TextOperation that = (TextOperation) o;
        return timestamp == that.timestamp &&
               type == that.type &&
               Objects.equals(positionId, that.positionId) &&
               Objects.equals(siteId, that.siteId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, positionId, timestamp, siteId);
    }

    @Override
    public String toString() {
        return String.format("%s[pos=%s, char=%s, ts=%d, site=%s]",
            type, positionId, character, timestamp, siteId);
    }
}