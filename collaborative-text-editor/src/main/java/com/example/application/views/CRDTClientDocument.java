package com.example.application.views;

import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A client-side CRDT document that handles character-wise operations
 * with Lamport timestamps for conflict resolution.
 */
public class CRDTClientDocument {
    private static final Logger logger = LoggerFactory.getLogger(CRDTClientDocument.class);
    
    // Character-based CRDT structure
    private final List<CharacterCRDT> characters = new ArrayList<>();
    private final Set<String> appliedOperationIds = new HashSet<>();
    private final String userId;
    private int localClock = 0;
    
    public CRDTClientDocument(String userId) {
        this.userId = userId;
    }
    
    /**
     * Apply an operation to the document
     */
    public void apply(Operation op) {
        // Avoid applying the same operation twice
        String operationId = op.getUserId() + ":" + op.getTimestamp();
        if (appliedOperationIds.contains(operationId)) {
            logger.info("Operation already applied, skipping: {}", operationId);
            return;
        }
        
        try {
            switch (op.getType()) {
                case INSERT -> handleInsert(op);
                case DELETE -> handleDelete(op);
                case UPDATE -> handleFullUpdate(op);
            }
            
            appliedOperationIds.add(operationId);
            localClock = Math.max(localClock, (int) op.getTimestamp() + 1);
            
        } catch (Exception e) {
            logger.error("Error applying operation: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Handle character insertion
     */
    private void handleInsert(Operation op) {
        if (op.getContent() == null || op.getContent().isEmpty()) {
            return;
        }
        
        int position = op.getPosition();
        if (position < 0) position = 0;
        if (position > characters.size()) position = characters.size();
        
        // Calculate position identifiers
        String before = position > 0 ? characters.get(position - 1).getPositionId() : "0";
        String after = position < characters.size() ? characters.get(position).getPositionId() : "z";
        
        // Insert each character with a unique position ID between before and after
        char[] chars = op.getContent().toCharArray();
        for (int i = 0; i < chars.length; i++) {
            String newPosition = generatePositionBetween(before, after);
            CharacterCRDT charCRDT = new CharacterCRDT(chars[i], newPosition, op.getUserId(), op.getTimestamp() + i);
            
            // Find the correct position to insert
            int insertAt = findInsertionPoint(newPosition);
            characters.add(insertAt, charCRDT);
            
            // Update before for next character
            before = newPosition;
        }
    }
    
    /**
     * Handle character deletion
     */
    private void handleDelete(Operation op) {
        int position = op.getPosition();
        int length = op.getContent() != null ? op.getContent().length() : 1;
        
        // Validate position
        if (position < 0 || position >= characters.size()) {
            return;
        }
        
        // Make sure we don't delete beyond the document end
        int endPos = Math.min(position + length, characters.size());
        
        // Remove characters from position to endPos
        for (int i = endPos - 1; i >= position; i--) {
            if (i < characters.size()) {
                characters.remove(i);
            }
        }
    }
    
    /**
     * Handle full document update (less efficient, use only for initialization or sync)
     */
    private void handleFullUpdate(Operation op) {
        characters.clear();
        if (op.getContent() == null) {
            return;
        }
        
        String content = op.getContent();
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            // Generate evenly spaced positions for initial content
            String posId = generatePositionId(i, content.length());
            characters.add(new CharacterCRDT(c, posId, op.getUserId(), op.getTimestamp()));
        }
    }
    
    /**
     * Generate a position ID between two existing positions
     */
    private String generatePositionBetween(String before, String after) {
        if (before.equals(after)) {
            return before + "0";
        }
        
        if (before.length() == after.length()) {
            if (before.compareTo(after) < 0) {
                // Simple case: generate a string between the two
                int beforeVal = before.charAt(before.length() - 1);
                int afterVal = after.charAt(after.length() - 1);
                
                if (afterVal - beforeVal > 1) {
                    // There's space between the characters
                    char middle = (char) ((beforeVal + afterVal) / 2);
                    return before.substring(0, before.length() - 1) + middle;
                } else {
                    // No space between characters, append a middle value
                    return before + "5";
                }
            } else {
                // Swap them if before is greater than after (shouldn't happen, but just in case)
                return generatePositionBetween(after, before);
            }
        } else {
            // Different lengths
            if (before.length() < after.length() && after.startsWith(before)) {
                return before + "5";
            } else if (after.length() < before.length() && before.startsWith(after)) {
                return after + "5";
            } else {
                // Find common prefix
                int commonLength = 0;
                while (commonLength < Math.min(before.length(), after.length()) && 
                       before.charAt(commonLength) == after.charAt(commonLength)) {
                    commonLength++;
                }
                
                if (commonLength == 0) {
                    // No common prefix, just use middle ASCII value
                    return String.valueOf((char) ((before.charAt(0) + after.charAt(0)) / 2));
                }
                
                String commonPrefix = before.substring(0, commonLength);
                
                if (commonLength < before.length() && commonLength < after.length()) {
                    char beforeChar = before.charAt(commonLength);
                    char afterChar = after.charAt(commonLength);
                    
                    if (afterChar - beforeChar > 1) {
                        // There's space between the characters
                        char middle = (char) ((beforeChar + afterChar) / 2);
                        return commonPrefix + middle;
                    }
                }
                
                return commonPrefix + "5";
            }
        }
    }
    
    /**
     * Generate evenly spaced position IDs for initial content
     */
    private String generatePositionId(int index, int totalChars) {
        // Use base36 encoding to create compact, ordered position IDs
        if (totalChars <= 1) {
            return "a0";
        }
        
        // Create a range of positions from "a0" to "z0"
        char base = 'a';
        int range = 'z' - 'a';
        
        // Calculate evenly spaced positions
        int step = range / (totalChars + 1);
        char pos = (char) (base + (step * (index + 1)));
        
        return String.valueOf(pos) + "0";
    }
    
    /**
     * Find the correct insertion point for a character based on its position ID
     */
    private int findInsertionPoint(String positionId) {
        for (int i = 0; i < characters.size(); i++) {
            if (characters.get(i).getPositionId().compareTo(positionId) > 0) {
                return i;
            }
        }
        return characters.size();
    }
    
    /**
     * Get the current plain text content of the document
     */
    public String getContent() {
        StringBuilder sb = new StringBuilder();
        for (CharacterCRDT character : characters) {
            sb.append(character.getCharacter());
        }
        return sb.toString();
    }
    
    /**
     * Initialize document with content from server
     */
    public void setInitialContent(String content) {
        characters.clear();
        appliedOperationIds.clear();
        
        // Create an update operation and apply it
        Operation op = new Operation.Builder()
            .type(Operation.Type.UPDATE)
            .userId("server")
            .content(content)
            .timestamp(System.currentTimeMillis())
            .build();
            
        apply(op);
    }
    
    /**
     * Generate a new insertion operation
     */
    public Operation createInsertOperation(String content, int position) {
        return new Operation.Builder()
            .type(Operation.Type.INSERT)
            .userId(userId)
            .content(content)
            .position(position)
            .timestamp(++localClock)
            .build();
    }
    
    /**
     * Generate a new deletion operation
     */
    public Operation createDeleteOperation(int position, int length) {
        StringBuilder deletedContent = new StringBuilder();
        for (int i = position; i < position + length && i < characters.size(); i++) {
            deletedContent.append(characters.get(i).getCharacter());
        }
        
        return new Operation.Builder()
            .type(Operation.Type.DELETE)
            .userId(userId)
            .content(deletedContent.toString())
            .position(position)
            .timestamp(++localClock)
            .build();
    }
    
    /**
     * Represents a single character in the CRDT
     */
    private static class CharacterCRDT {
        private final char character;
        private final String positionId;  // Fractional position identifier
        private final String userId;      // User who created this character
        private final long timestamp;     // Lamport timestamp
        
        public CharacterCRDT(char character, String positionId, String userId, long timestamp) {
            this.character = character;
            this.positionId = positionId;
            this.userId = userId;
            this.timestamp = timestamp;
        }
        
        public char getCharacter() {
            return character;
        }
        
        public String getPositionId() {
            return positionId;
        }
        
        public String getUserId() {
            return userId;
        }
        
        public long getTimestamp() {
            return timestamp;
        }
    }
}