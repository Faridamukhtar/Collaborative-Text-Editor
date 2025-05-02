
package com.example.application.views.pageeditor.socketsLogic;
import com.example.application.views.pageeditor.CRDT.*;

import com.vaadin.flow.shared.Registration;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * A mock implementation of CollaborationService for testing
 */
public class MockCollaborationService implements CollaborationService {
    private final List<Consumer<TextOperation>> operationListeners = new CopyOnWriteArrayList<>();
    private final List<Runnable> connectionListeners = new CopyOnWriteArrayList<>();
    private final String initialContent;
    private boolean connected = false;

    /**
     * Create a new mock collaboration service
     * @param initialContent The initial content to use
     */
    public MockCollaborationService(String initialContent) {
        this.initialContent = initialContent;
    }

    @Override
    public void connect() {
        connected = true;
        notifyConnectionChanged();
    }

    @Override
    public void disconnect() {
        connected = false;
        notifyConnectionChanged();
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public void sendOperation(TextOperation operation) {
        System.out.println("Sent operation: " + operation);
        
        // Simulate network delay and echo the operation back
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                operationListeners.forEach(listener -> listener.accept(operation));
                System.out.println("Echoed operation: " + operation);
            }
        }, 200); // 200ms delay
    }

    @Override
    public Registration subscribeToConnectionChanges(Runnable listener) {
        connectionListeners.add(listener);
        return () -> connectionListeners.remove(listener);
    }

    @Override
    public CompletableFuture<String> getInitialContent() {
        return CompletableFuture.completedFuture(initialContent);
    }

    @Override
    public Registration subscribeToOperations(Consumer<TextOperation> listener) {
        operationListeners.add(listener);
        return () -> operationListeners.remove(listener);
    }
    
    private void notifyConnectionChanged() {
        connectionListeners.forEach(listener -> {
            try {
                listener.run();
            } catch (Exception e) {
                System.err.println("Error in connection listener: " + e.getMessage());
            }
        });
    }
}