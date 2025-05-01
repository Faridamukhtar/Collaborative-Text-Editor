package com.example.application.views.pageeditor.CRDT;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DeltaCalculator {
    public static List<TextOperation> calculateOperations(String oldText, String newText) {
        List<TextOperation> operations = new ArrayList<>();
        
        // Implementation of Myers diff algorithm or similar
        // This simplified version handles basic inserts/deletes
        
        int oldLength = oldText.length();
        int newLength = newText.length();
        int commonPrefix = 0;
        
        // Find common prefix
        while (commonPrefix < oldLength && commonPrefix < newLength && 
               oldText.charAt(commonPrefix) == newText.charAt(commonPrefix)) {
            commonPrefix++;
        }
        
        // Find common suffix
        int commonSuffix = 0;
        while (commonSuffix < oldLength - commonPrefix && 
               commonSuffix < newLength - commonPrefix &&
               oldText.charAt(oldLength - 1 - commonSuffix) == 
               newText.charAt(newLength - 1 - commonSuffix)) {
            commonSuffix++;
        }
        
        // Handle deletions
        if (oldLength > commonPrefix + commonSuffix) {
            for (int i = commonPrefix; i < oldLength - commonSuffix; i++) {
                operations.add(TextOperation.createDelete(
                    String.valueOf(i),
                    System.currentTimeMillis(),
                    "local"
                ));
            }
        }
        
        // Handle insertions
        if (newLength > commonPrefix + commonSuffix) {
            String insertedText = newText.substring(
                commonPrefix,
                newLength - commonSuffix
            );
            
            for (int i = 0; i < insertedText.length(); i++) {
                operations.add(TextOperation.createInsert(
                    insertedText.charAt(i),
                    String.valueOf(commonPrefix + i),
                    System.currentTimeMillis(),
                    "local",
                    String.valueOf(commonPrefix + i - 1),
                    String.valueOf(commonPrefix + i + 1)
                ));
            }
        }
        
        return operations;
    }
}