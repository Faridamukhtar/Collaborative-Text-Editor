package com.collab.backend.connections.model;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class OperationBuffer {
    private List<String> missedOperations;  // List of operations that were missed

    public OperationBuffer() {
        this.missedOperations = new CopyOnWriteArrayList<>();
    }

    // Add an operation to the buffer
    public void addOperation(String operation) {
        missedOperations.add(operation);
    }

    // Get the list of missed operations
    public List<String> getOperations() {
        return missedOperations;
    }

    // Clear the buffer after the operations are sent to the user
    public void clear() {
        missedOperations.clear();
    }
}
