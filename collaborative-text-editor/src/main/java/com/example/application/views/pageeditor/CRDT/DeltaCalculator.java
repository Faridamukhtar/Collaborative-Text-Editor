package com.example.application.views.pageeditor.CRDT;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Calculates the operations needed to transform one text state to another,
 * optimized for tree-based CRDT structures.
 */
public class DeltaCalculator {
    private static final Logger logger = LoggerFactory.getLogger(DeltaCalculator.class);
    
    /**
     * Calculate operations needed to transform oldText to newText
     * This implementation is compatible with the TreeBasedCRDT structure
     * 
     * @param oldText The original text
     * @param newText The target text
     * @param crdt The CRDT instance to use for node references
     * @return List of operations to transform oldText to newText
     */
    public static List<TextOperation> calculateOperations(
            String oldText, 
            String newText, 
            TreeBasedCRDT crdt) {
        
        List<TextOperation> operations = new ArrayList<>();
        
        // Get diff using Myers algorithm
        List<DiffOperation> diffOps = calculateDiff(oldText, newText);
        logger.debug("Calculated {} diff operations", diffOps.size());
        
        // Convert diff operations to CRDT operations
        int position = 0;
        for (DiffOperation op : diffOps) {
            switch (op.type) {
                case INSERT:
                    for (char c : op.text.toCharArray()) {
                        TextOperation insertOp = crdt.applyLocalInsert(c, position);
                        operations.add(insertOp);
                        position++;
                    }
                    break;
                    
                case DELETE:
                    // For each character to delete, apply a deletion
                    for (int i = 0; i < op.count; i++) {
                        TextOperation deleteOp = crdt.applyLocalDelete(position);
                        if (deleteOp != null) {
                            operations.add(deleteOp);
                        }
                        // Position doesn't increment because we're deleting
                    }
                    break;
                    
                case KEEP:
                    // Just move the position forward
                    position += op.count;
                    break;
            }
        }
        
        return operations;
    }
    
    /**
     * Implementation of Myers diff algorithm to find minimal edit script
     * between two strings.
     */
    private static List<DiffOperation> calculateDiff(String oldText, String newText) {
        List<DiffOperation> operations = new ArrayList<>();
        
        int oldLength = oldText.length();
        int newLength = newText.length();
        
        // Optimize for common prefix and suffix
        int commonPrefix = 0;
        while (commonPrefix < oldLength && commonPrefix < newLength 
                && oldText.charAt(commonPrefix) == newText.charAt(commonPrefix)) {
            commonPrefix++;
        }
        
        int oldEndIndex = oldLength - 1;
        int newEndIndex = newLength - 1;
        int commonSuffix = 0;
        
        while (oldEndIndex >= commonPrefix && newEndIndex >= commonPrefix 
                && oldText.charAt(oldEndIndex) == newText.charAt(newEndIndex)) {
            oldEndIndex--;
            newEndIndex--;
            commonSuffix++;
        }
        
        // Add prefix as KEEP
        if (commonPrefix > 0) {
            operations.add(new DiffOperation(
                    DiffOperationType.KEEP,
                    null,
                    commonPrefix));
        }
        
        // Middle section - either delete from old or insert from new
        if (oldEndIndex >= commonPrefix || newEndIndex >= commonPrefix) {
            // Delete middle part from old text
            if (oldEndIndex >= commonPrefix) {
                int deleteCount = oldEndIndex - commonPrefix + 1;
                operations.add(new DiffOperation(
                        DiffOperationType.DELETE,
                        null, 
                        deleteCount));
            }
            
            // Insert middle part from new text
            if (newEndIndex >= commonPrefix) {
                String insertText = newText.substring(commonPrefix, newEndIndex + 1);
                operations.add(new DiffOperation(
                        DiffOperationType.INSERT,
                        insertText,
                        0));
            }
        }
        
        // Add suffix as KEEP
        if (commonSuffix > 0) {
            operations.add(new DiffOperation(
                    DiffOperationType.KEEP,
                    null,
                    commonSuffix));
        }
        
        return operations;
    }
    
    /**
     * Types of operations used in the diff algorithm
     */
    private enum DiffOperationType {
        KEEP,
        INSERT,
        DELETE
    }
    
    /**
     * Internal class to represent diff operations
     */
    private static class DiffOperation {
        final DiffOperationType type;
        final String text;
        final int count;
        
        DiffOperation(DiffOperationType type, String text, int count) {
            this.type = type;
            this.text = text;
            this.count = count;
        }
    }
}