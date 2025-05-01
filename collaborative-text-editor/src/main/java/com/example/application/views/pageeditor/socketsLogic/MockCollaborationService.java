package com.example.application.views.pageeditor.socketsLogic;
import com.example.application.views.pageeditor.CRDT.*;

import com.vaadin.flow.shared.Registration;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class MockCollaborationService implements CollaborationService {
    private final List<Consumer<TextOperation>> operationListeners = new CopyOnWriteArrayList<>();
    private final String initialContent;
    private boolean connected = false;

    public MockCollaborationService(String initialContent) {
        this.initialContent = initialContent;
    }

    public void connect() {
        connected = true;
    }

    public void disconnect() {
        connected = false;
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public void sendOperation(TextOperation operation) {
        System.out.println("Operation: " + operation);
        
        // Simulate network delay
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                operationListeners.forEach(listener -> listener.accept(operation));
            }
        }, 200); // 200ms delay
    }

    public Registration subscribeToConnectionChanges(Runnable listener) {
        // Not implemented for mock
        return () -> {};
    }

    @Override
    public CompletableFuture<String> requestInitialState() {
        return CompletableFuture.completedFuture(initialContent);
    }

    @Override
    public Registration subscribeToChanges(Consumer<TextOperation> listener) {
        operationListeners.add(listener);
        return () -> operationListeners.remove(listener);
    }
}