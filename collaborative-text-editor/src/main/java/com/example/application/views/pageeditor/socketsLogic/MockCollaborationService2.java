package com.example.application.views.pageeditor.socketsLogic;
import com.example.application.views.pageeditor.CRDT.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import com.vaadin.flow.shared.Registration;

/**
 * Mock implementation of the CollaborationService for testing
 */
public class MockCollaborationService2 implements CollaborationService {
    private final List<Consumer<TextOperation>> operationListeners = new ArrayList<>();
    private final List<Runnable> connectionListeners = new ArrayList<>();
    private String content;
    private final String clientId;
    private boolean connected = false;
    private SharedMockCollaborationService sharedService;
    
    /**
     * Create a mock service with initial content and a random client ID
     * @param initialContent The initial content
     */
    public MockCollaborationService2(String initialContent) {
        this(initialContent, UUID.randomUUID().toString());
    }
    
    /**
     * Create a mock service with initial content and specific client ID
     * @param initialContent The initial content
     * @param clientId The client ID
     */
    public MockCollaborationService2(String initialContent, String clientId) {
        this.content = initialContent;
        this.clientId = clientId;
    }
    
    /**
     * Set the shared service for this mock service
     * @param sharedService The shared service to use
     */
    public void setSharedService(SharedMockCollaborationService sharedService) {
        this.sharedService = sharedService;
    }
    
    /**
     * Get the client ID for this service
     * @return The client ID
     */
    @Override
    public String getClientId() {
        return clientId;
    }
    
    @Override
    public void connect() {
        connected = true;
        notifyConnectionListeners();
    }
    
    @Override
    public void disconnect() {
        connected = false;
        notifyConnectionListeners();
    }
    
    @Override
    public boolean isConnected() {
        return connected;
    }
    
    @Override
    public void sendOperation(TextOperation operation) {
        // If we have a shared service, broadcast the operation
        if (sharedService != null) {
            sharedService.broadcastOperation(operation, clientId);
        }
    }
    
    @Override
    public Registration subscribeToOperations(Consumer<TextOperation> listener) {
        operationListeners.add(listener);
        return () -> operationListeners.remove(listener);
    }
    
    @Override
    public Registration subscribeToConnectionChanges(Runnable listener) {
        connectionListeners.add(listener);
        return () -> connectionListeners.remove(listener);
    }
    
    @Override
    public CompletableFuture<String> getInitialContent() {
        // Get content from the shared service if available
        if (sharedService != null) {
            return CompletableFuture.completedFuture(sharedService.getInitialContent());
        }
        return CompletableFuture.completedFuture(content);
    }
    
    /**
     * Method to receive operations from the shared service
     * @param operation The operation to receive
     */
    public void receiveRemoteOperation(TextOperation operation) {
        for (Consumer<TextOperation> listener : operationListeners) {
            System.out.println("Received operation: " + operation); 
            System.out.println("Content before operation: " + content);
            System.out.println("Client ID: " + clientId);
            System.out.println("listener: " + listener);
            listener.accept(operation);
        }
    }
    
    private void notifyConnectionListeners() {
        for (Runnable listener : connectionListeners) {
            listener.run();
        }
    }
}