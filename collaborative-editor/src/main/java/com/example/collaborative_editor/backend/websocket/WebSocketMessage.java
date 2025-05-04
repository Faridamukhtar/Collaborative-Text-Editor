package com.example.collaborative_editor.backend.websocket;

import com.example.collaborative_editor.backend.crdt.CrdtChar;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class WebSocketMessage {
    private String type;
    private String userId;
    private String sessionId;
    private long timestamp;
    private CharacterDTO character;
    private String documentId;
    private String content;
    
    // Nested class for character data transfer
    public static class CharacterDTO {
        private String value;
        private PositionDTO[] position;
        private boolean isDeleted;
        
        public String getValue() {
            return value;
        }
        
        public void setValue(String value) {
            this.value = value;
        }
        
        public PositionDTO[] getPosition() {
            return position;
        }
        
        public void setPosition(PositionDTO[] position) {
            this.position = position;
        }
        
        public boolean isDeleted() {
            return isDeleted;
        }
        
        public void setDeleted(boolean deleted) {
            isDeleted = deleted;
        }
    }
    
    // Nested class for position data transfer
    public static class PositionDTO {
        private int digit;
        private String siteId;
        
        public int getDigit() {
            return digit;
        }
        
        public void setDigit(int digit) {
            this.digit = digit;
        }
        
        public String getSiteId() {
            return siteId;
        }
        
        public void setSiteId(String siteId) {
            this.siteId = siteId;
        }
    }
    
    // Getters and setters
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public String getSessionId() {
        return sessionId;
    }
    
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    public CharacterDTO getCharacter() {
        return character;
    }
    
    public void setCharacter(CharacterDTO character) {
        this.character = character;
    }
    
    public String getDocumentId() {
        return documentId;
    }
    
    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}