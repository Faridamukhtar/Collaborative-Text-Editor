package com.example.collaborative_editor.backend.crdt;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class CrdtChar {
    private final String value;
    private final List<Identifier> position;
    private final CharacterMetadata metadata;
    private boolean isDeleted;

    public CrdtChar(String value, List<Identifier> position, String siteId, long timestamp) {
        this.value = value;
        this.position = new ArrayList<>(position);
        this.metadata = new CharacterMetadata(siteId, timestamp);
        this.isDeleted = false;
    }

    public String getValue() {
        return value;
    }

    public List<Identifier> getPosition() {
        return new ArrayList<>(position);
    }

    public CharacterMetadata getMetadata() {
        return metadata;
    }

    public boolean isDeleted() {
        return isDeleted;
    }

    public void setDeleted(boolean deleted) {
        isDeleted = deleted;
    }

    public int comparePosition(List<Identifier> otherPosition) {
        int minLength = Math.min(position.size(), otherPosition.size());
        
        for (int i = 0; i < minLength; i++) {
            int comparison = position.get(i).compareTo(otherPosition.get(i));
            if (comparison != 0) {
                return comparison;
            }
        }
        
        return Integer.compare(position.size(), otherPosition.size());
    }
}