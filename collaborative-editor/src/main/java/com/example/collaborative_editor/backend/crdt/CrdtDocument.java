package com.example.collaborative_editor.backend.crdt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class CrdtDocument {
    private static final int BASE_BOUNDARY = 32;
    private static final int BASE_STEP = 16;
    
    private final List<CrdtChar> characters = new ArrayList<>();
    private final String siteId;
    
    public CrdtDocument(String siteId) {
        this.siteId = siteId;
    }
    
    public synchronized void applyOperation(Operation operation) {
        if (operation.getType() == Operation.Type.INSERT) {
            insertChar(operation.getCharacter());
        } else if (operation.getType() == Operation.Type.DELETE) {
            deleteChar(operation.getCharacter().getPosition());
        }
    }
    
    private void insertChar(CrdtChar character) {
        int index = findInsertIndex(character.getPosition());
        characters.add(index, character);
    }
    
    private void deleteChar(List<Identifier> position) {
        int index = findPositionIndex(position);
        if (index >= 0) {
            characters.get(index).setDeleted(true);
        }
    }
    
    private int findInsertIndex(List<Identifier> position) {
        for (int i = 0; i < characters.size(); i++) {
            if (characters.get(i).comparePosition(position) > 0) {
                return i;
            }
        }
        return characters.size();
    }
    
    private int findPositionIndex(List<Identifier> position) {
        for (int i = 0; i < characters.size(); i++) {
            if (comparePositions(characters.get(i).getPosition(), position) == 0) {
                return i;
            }
        }
        return -1;
    }
    
    private int comparePositions(List<Identifier> pos1, List<Identifier> pos2) {
        int minLength = Math.min(pos1.size(), pos2.size());
        
        for (int i = 0; i < minLength; i++) {
            int comparison = pos1.get(i).compareTo(pos2.get(i));
            if (comparison != 0) {
                return comparison;
            }
        }
        
        return Integer.compare(pos1.size(), pos2.size());
    }
    
    public List<Identifier> generatePositionBetween(List<Identifier> before, List<Identifier> after) {
        // Special case: insert at beginning
        if (before == null || before.isEmpty()) {
            List<Identifier> newPos = new ArrayList<>();
            newPos.add(new Identifier(BASE_BOUNDARY / 2, siteId));
            return newPos;
        }
        
        // Special case: insert at end
        if (after == null || after.isEmpty()) {
            List<Identifier> newPos = new ArrayList<>();
            newPos.add(new Identifier(before.get(0).getDigit() + BASE_STEP, siteId));
            return newPos;
        }
        
        // General case: insert between two positions
        return generatePositionBetween(before, after, 0);
    }
    
    private List<Identifier> generatePositionBetween(List<Identifier> before, List<Identifier> after, int level) {
        List<Identifier> newPosition = new ArrayList<>();
        
        // Copy common prefix
        for (int i = 0; i < level; i++) {
            if (i < before.size()) {
                newPosition.add(before.get(i));
            }
        }
        
        int beforeDigit = (level < before.size()) ? before.get(level).getDigit() : 0;
        int afterDigit = (level < after.size()) ? after.get(level).getDigit() : BASE_BOUNDARY;
        
        // If there's enough space between digits
        if (afterDigit - beforeDigit > 1) {
            int newDigit = beforeDigit + (afterDigit - beforeDigit) / 2;
            newPosition.add(new Identifier(newDigit, siteId));
            return newPosition;
        }
        
        // Not enough space, need to go deeper
        if (level < before.size()) {
            newPosition.add(before.get(level));
        } else {
            newPosition.add(new Identifier(beforeDigit, siteId));
        }
        
        // Either continue deeper or add a new level
        if (level + 1 < before.size() && level + 1 < after.size() && 
            before.get(level).equals(after.get(level))) {
            List<Identifier> deeperPosition = generatePositionBetween(before, after, level + 1);
            // Add only the deeper parts (skip the shared prefix we already added)
            for (int i = level + 1; i < deeperPosition.size(); i++) {
                newPosition.add(deeperPosition.get(i));
            }
        } else {
            // Add a new level with site ID to ensure uniqueness
            newPosition.add(new Identifier(BASE_STEP, siteId));
        }
        
        return newPosition;
    }
    
    public CrdtChar getCharacterAt(int index) {
        List<CrdtChar> visibleChars = getVisibleChars();
        if (index < 0 || index >= visibleChars.size()) {
            return null;
        }
        return visibleChars.get(index);
    }
    
    public List<CrdtChar> getVisibleChars() {
        List<CrdtChar> visibleChars = new ArrayList<>();
        for (CrdtChar ch : characters) {
            if (!ch.isDeleted()) {
                visibleChars.add(ch);
            }
        }
        return visibleChars;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (CrdtChar ch : characters) {
            if (!ch.isDeleted()) {
                sb.append(ch.getValue());
            }
        }
        return sb.toString();
    }
}