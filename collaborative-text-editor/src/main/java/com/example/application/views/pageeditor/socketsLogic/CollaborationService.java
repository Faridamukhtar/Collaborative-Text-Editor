package com.example.application.views.pageeditor.socketsLogic;

import com.example.application.views.pageeditor.CRDT.TextOperation;
import com.vaadin.flow.shared.Registration;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Interface for collaboration services that handle network communication
 * for collaborative editing.
 */
public interface CollaborationService {
    
    /**
     * Connect to the collaboration service
     */
    void connect();
    
    /**
     * Disconnect from the collaboration service
     */
    void disconnect();
    
    /**
     * Check if the service is connected
     * 
     * @return True if connected, false otherwise
     */
    boolean isConnected();
    
    /**
     * Get the initial content of the document
     * 
     * @return A future with the initial content
     */
    CompletableFuture<String> getInitialContent();
    
    /**
     * Send a text operation to other collaborators
     * 
     * @param operation The operation to send
     */
    void sendOperation(TextOperation operation);
    
    /**
     * Subscribe to incoming text operations
     * 
     * @param listener The listener to call when operations are received
     * @return A registration that can be used to unsubscribe
     */
    Registration subscribeToOperations(Consumer<TextOperation> listener);
    
    /**
     * Subscribe to changes in connection state
     * 
     * @param listener The listener to call when connection state changes
     * @return A registration that can be used to unsubscribe
     */
    Registration subscribeToConnectionChanges(Runnable listener);

    /**
     * Get the client ID of this service
     * 
     * @return The client ID
     */
    String getClientId();
}