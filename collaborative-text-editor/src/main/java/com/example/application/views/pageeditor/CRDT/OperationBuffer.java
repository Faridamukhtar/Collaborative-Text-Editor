package com.example.application.views.pageeditor.CRDT;

import java.util.*;
import java.util.concurrent.*;

public class OperationBuffer {
    private final PriorityBlockingQueue<TextOperation> pendingOperations = 
        new PriorityBlockingQueue<>(11, Comparator.comparingLong(TextOperation::getTimestamp));

    public void add(TextOperation op) {
        pendingOperations.add(op);
    }

    public List<TextOperation> getPendingOperations() {
        return new ArrayList<>(pendingOperations);
    }

    public void acknowledge(String positionId) {
        pendingOperations.removeIf(op -> op.getPositionId().equals(positionId));
    }

    public boolean isEmpty() {
        return pendingOperations.isEmpty();
    }
}