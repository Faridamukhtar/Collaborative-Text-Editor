package com.collab.backend;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for calculating differences between text strings
 * and creating Delta operations
 */
public class DeltaDiff {

    /**
     * Calculate the differences between the old content and new content
     * and create a list of delta operations.
     * 
     * @param oldContent The previous content
     * @param newContent The new content
     * @param userId The user making the change
     * @param nodeId The ID of the node being modified
     * @return A list of operations representing the delta
     */
    public static List<Operation> calculateDelta(
            String oldContent, 
            String newContent, 
            String userId, 
            String nodeId) {
        
        if (oldContent == null) oldContent = "";
        if (newContent == null) newContent = "";
        
        List<Operation> operations = new ArrayList<>();
        
        // Use the Myers diff algorithm to find the longest common subsequence
        List<DiffOperation> diffs = diff(oldContent, newContent);
        
        int currentPosition = 0;
        
        for (DiffOperation diff : diffs) {
            switch (diff.type) {
                case EQUAL:
                    // Equal parts are retained
                    currentPosition += diff.text.length();
                    break;
                    
                case INSERT:
                    // Insert operation
                    operations.add(new Operation.Builder()
                            .type(Operation.Type.DELTA)
                            .deltaType(Operation.DeltaType.INSERT)
                            .nodeId(nodeId)
                            .content(diff.text)
                            .startOffset(currentPosition)
                            .userId(userId)
                            .build());
                    currentPosition += diff.text.length();
                    break;
                    
                case DELETE:
                    // Delete operation
                    operations.add(new Operation.Builder()
                            .type(Operation.Type.DELTA)
                            .deltaType(Operation.DeltaType.DELETE)
                            .nodeId(nodeId)
                            .startOffset(currentPosition)
                            .endOffset(currentPosition + diff.text.length())
                            .userId(userId)
                            .build());
                    break;
            }
        }
        
        return operations;
    }
    
    /**
     * Apply a list of delta operations to a string
     * 
     * @param content The original content
     * @param operations The list of delta operations to apply
     * @return The new content after applying the operations
     */
    public static String applyDelta(String content, List<Operation> operations) {
        if (content == null) content = "";
        if (operations == null || operations.isEmpty()) return content;
        
        StringBuilder result = new StringBuilder(content);
        
        // Sort operations by position, with deletes before inserts
        operations.sort((a, b) -> {
            if (a.getDeltaType() == Operation.DeltaType.DELETE && 
                b.getDeltaType() == Operation.DeltaType.INSERT) {
                return -1;
            } else if (a.getDeltaType() == Operation.DeltaType.INSERT && 
                       b.getDeltaType() == Operation.DeltaType.DELETE) {
                return 1;
            } else {
                return Integer.compare(a.getStartOffset(), b.getStartOffset());
            }
        });
        
        // Apply operations from end to beginning to avoid position changes
        for (int i = operations.size() - 1; i >= 0; i--) {
            Operation op = operations.get(i);
            
            if (op.getDeltaType() == Operation.DeltaType.INSERT) {
                result.insert(op.getStartOffset(), op.getContent());
            } else if (op.getDeltaType() == Operation.DeltaType.DELETE) {
                result.delete(op.getStartOffset(), op.getEndOffset());
            }
        }
        
        return result.toString();
    }
    
    // Enum for diff operations
    public enum DiffType {
        EQUAL, INSERT, DELETE
    }
    
    // Diff operation class
    public static class DiffOperation {
        public final DiffType type;
        public final String text;
        
        public DiffOperation(DiffType type, String text) {
            this.type = type;
            this.text = text;
        }
    }
    
    /**
     * Implementation of the basic diff algorithm using longest common subsequence
     * For production use, consider using a more optimized library like google-diff-match-patch
     */
    private static List<DiffOperation> diff(String text1, String text2) {
        List<DiffOperation> diffs = new ArrayList<>();
        
        if (text1.equals(text2)) {
            if (!text1.isEmpty()) {
                diffs.add(new DiffOperation(DiffType.EQUAL, text1));
            }
            return diffs;
        }
        
        // Find the longest common substring
        String commonSubstring = longestCommonSubstring(text1, text2);
        
        if (commonSubstring.isEmpty()) {
            // No common substring, just return delete and insert
            if (!text1.isEmpty()) {
                diffs.add(new DiffOperation(DiffType.DELETE, text1));
            }
            if (!text2.isEmpty()) {
                diffs.add(new DiffOperation(DiffType.INSERT, text2));
            }
            return diffs;
        }
        
        // Find where the substring starts in each text
        int pos1 = text1.indexOf(commonSubstring);
        int pos2 = text2.indexOf(commonSubstring);
        
        // Get the parts before and after the common substring
        String prefix1 = text1.substring(0, pos1);
        String suffix1 = text1.substring(pos1 + commonSubstring.length());
        
        String prefix2 = text2.substring(0, pos2);
        String suffix2 = text2.substring(pos2 + commonSubstring.length());
        
        // Recursively diff the prefixes
        diffs.addAll(diff(prefix1, prefix2));
        
        // Add the common substring
        diffs.add(new DiffOperation(DiffType.EQUAL, commonSubstring));
        
        // Recursively diff the suffixes
        diffs.addAll(diff(suffix1, suffix2));
        
        return diffs;
    }
    
    /**
     * Find the longest common substring between two strings
     */
    private static String longestCommonSubstring(String s1, String s2) {
        if (s1.isEmpty() || s2.isEmpty()) {
            return "";
        }
        
        int[][] lengths = new int[s1.length() + 1][s2.length() + 1];
        int maxLength = 0;
        int endIndex = 0;
        
        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
                    lengths[i][j] = lengths[i - 1][j - 1] + 1;
                    if (lengths[i][j] > maxLength) {
                        maxLength = lengths[i][j];
                        endIndex = i;
                    }
                }
            }
        }
        
        if (maxLength == 0) {
            return "";
        }
        
        return s1.substring(endIndex - maxLength, endIndex);
    }
}