package com.example.application.views;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for computing differences between document versions
 * and generating operations to transform one version into another.
 */
public class DocumentDiff {
    private static final Logger logger = LoggerFactory.getLogger(DocumentDiff.class);
    
    // Myers diff algorithm implementation for character-level diffing
    public static List<Operation> computeDiff(String oldText, String newText, String userId) {
        List<Operation> operations = new ArrayList<>();
        
        if (oldText == null) oldText = "";
        if (newText == null) newText = "";
        
        // If texts are identical, no operations needed
        if (oldText.equals(newText)) {
            return operations;
        }
        
        // For large text differences, just use UPDATE operation
        if (Math.abs(oldText.length() - newText.length()) > oldText.length() * 0.5) {
            Operation updateOp = new Operation.Builder()
                .type(Operation.Type.UPDATE)
                .userId(userId)
                .content(newText)
                .timestamp(System.currentTimeMillis())
                .build();
            operations.add(updateOp);
            return operations;
        }
        
        // Compute LCS (Longest Common Subsequence) matrix
        int[][] lcs = computeLCS(oldText, newText);
        
        // Backtrack to find the operations
        List<DiffOp> diffOps = backtrack(lcs, oldText, newText, oldText.length(), newText.length());
        
        // Consolidate operations
        int position = 0;
        StringBuilder insertBuffer = new StringBuilder();
        StringBuilder deleteBuffer = new StringBuilder();
        int deleteStart = -1;
        
        for (DiffOp op : diffOps) {
            switch (op.type) {
                case EQUAL:
                    // Flush any buffered operations
                    if (insertBuffer.length() > 0) {
                        // Add INSERT operation
                        operations.add(new Operation.Builder()
                            .type(Operation.Type.INSERT)
                            .userId(userId)
                            .content(insertBuffer.toString())
                            .position(position - insertBuffer.length())
                            .timestamp(System.currentTimeMillis())
                            .build());
                        insertBuffer.setLength(0);
                    }
                    
                    if (deleteBuffer.length() > 0) {
                        // Add DELETE operation
                        operations.add(new Operation.Builder()
                            .type(Operation.Type.DELETE)
                            .userId(userId)
                            .content(deleteBuffer.toString())
                            .position(deleteStart)
                            .timestamp(System.currentTimeMillis())
                            .build());
                        deleteBuffer.setLength(0);
                        deleteStart = -1;
                    }
                    
                    position++;
                    break;
                    
                case INSERT:
                    insertBuffer.append(op.character);
                    position++;
                    break;
                    
                case DELETE:
                    if (deleteStart == -1) {
                        deleteStart = position;
                    }
                    deleteBuffer.append(op.character);
                    break;
            }
        }
        
        // Flush any remaining operations
        if (insertBuffer.length() > 0) {
            operations.add(new Operation.Builder()
                .type(Operation.Type.INSERT)
                .userId(userId)
                .content(insertBuffer.toString())
                .position(position - insertBuffer.length())
                .timestamp(System.currentTimeMillis())
                .build());
        }
        
        if (deleteBuffer.length() > 0) {
            operations.add(new Operation.Builder()
                .type(Operation.Type.DELETE)
                .userId(userId)
                .content(deleteBuffer.toString())
                .position(deleteStart)
                .timestamp(System.currentTimeMillis())
                .build());
        }
        
        return operations;
    }
    
    private static int[][] computeLCS(String str1, String str2) {
        int m = str1.length();
        int n = str2.length();
        int[][] lcs = new int[m + 1][n + 1];
        
        for (int i = 0; i <= m; i++) {
            for (int j = 0; j <= n; j++) {
                if (i == 0 || j == 0) {
                    lcs[i][j] = 0;
                } else if (str1.charAt(i - 1) == str2.charAt(j - 1)) {
                    lcs[i][j] = lcs[i - 1][j - 1] + 1;
                } else {
                    lcs[i][j] = Math.max(lcs[i - 1][j], lcs[i][j - 1]);
                }
            }
        }
        
        return lcs;
    }
    
    private static List<DiffOp> backtrack(int[][] lcs, String str1, String str2, int i, int j) {
        List<DiffOp> result = new ArrayList<>();
        
        if (i == 0 && j == 0) {
            return result;
        }
        
        if (i > 0 && j > 0 && str1.charAt(i - 1) == str2.charAt(j - 1)) {
            // Characters match
            result.addAll(backtrack(lcs, str1, str2, i - 1, j - 1));
            result.add(new DiffOp(DiffType.EQUAL, str1.charAt(i - 1)));
        } else if (j > 0 && (i == 0 || lcs[i][j - 1] >= lcs[i - 1][j])) {
            // Insert character from str2
            result.addAll(backtrack(lcs, str1, str2, i, j - 1));
            result.add(new DiffOp(DiffType.INSERT, str2.charAt(j - 1)));
        } else if (i > 0 && (j == 0 || lcs[i][j - 1] < lcs[i - 1][j])) {
            // Delete character from str1
            result.addAll(backtrack(lcs, str1, str2, i - 1, j));
            result.add(new DiffOp(DiffType.DELETE, str1.charAt(i - 1)));
        }
        
        return result;
    }
    
    private enum DiffType {
        EQUAL, INSERT, DELETE
    }
    
    private static class DiffOp {
        final DiffType type;
        final char character;
        
        DiffOp(DiffType type, char character) {
            this.type = type;
            this.character = character;
        }
    }
}