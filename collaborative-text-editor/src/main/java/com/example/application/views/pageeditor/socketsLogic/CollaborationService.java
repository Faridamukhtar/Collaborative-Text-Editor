package com.example.application.views.pageeditor.socketsLogic;
import com.example.application.views.pageeditor.CRDT.*;

import com.vaadin.flow.shared.Registration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Interface for collaboration services that handle communication
 * between clients for collaborative editing.
 */
public interface CollaborationService {
    /**
     * Request the initial document state from the server
     * @return A future that resolves to the document content
     */
    CompletableFuture<String> requestInitialState();
    
    /**
     * Check if the service is connected
     * @return true if connected, false otherwise
     */
    boolean isConnected();
    
    /**
     * Connect to the collaboration service
     */
    void connect();
    
    /**
     * Disconnect from the collaboration service
     */
    void disconnect();
    
    /**
     * Send an operation to other clients
     * @param operation The operation to send
     */
    void sendOperation(TextOperation operation);
    
    /**
     * Subscribe to changes from other clients
     * @param listener The callback to invoke when an operation is received
     * @return A registration that can be used to unsubscribe
     */
    Registration subscribeToChanges(Consumer<TextOperation> listener);
    
    /**
     * Subscribe to connection state changes
     * @param listener The callback to invoke when connection state changes
     * @return A registration that can be used to unsubscribe
     */
    Registration subscribeToConnectionChanges(Runnable listener);
}