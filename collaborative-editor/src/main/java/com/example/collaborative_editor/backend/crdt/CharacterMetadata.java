package com.example.collaborative_editor.backend.crdt;

import java.util.List;

public class CharacterMetadata {
    private final String siteId;
    private final long timestamp;

    public CharacterMetadata(String siteId, long timestamp) {
        this.siteId = siteId;
        this.timestamp = timestamp;
    }

    public String getSiteId() {
        return siteId;
    }

    public long getTimestamp() {
        return timestamp;
    }
}