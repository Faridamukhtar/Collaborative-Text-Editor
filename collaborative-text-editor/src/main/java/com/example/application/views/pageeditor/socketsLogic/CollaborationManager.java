
package com.example.application.views.pageeditor;

import com.example.application.views.pageeditor.CRDT.*;
import com.example.application.views.pageeditor.socketsLogic.CollaborationService;

import com.vaadin.flow.shared.Registration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Manages the collaboration between the CRDT data structure and the network service.
 * This class coordinates:
 * - Local text updates
 * - Remote operation handling
 * - Selection state synchronization
 */
public class CollaborationManager {
    private static final Logger logger = LoggerFactory.getLogger(CollaborationManager.class);
    
    private final TreeBasedCRDT crdt;
    private final CollaborationService collaborationService;
    private String localContent = "";
    private SelectionState selectionState = new SelectionState(0, 0);
    private Consumer<String> onContentChangeListener;
    private Registration connectionRegistration;
    
    /**
     * Create a new collaboration manager
     * 
     * @param collaborationService The service for network communication
     */
    public CollaborationManager(CollaborationService collaborationService) {
        this.collaborationService = collaborationService;
        this.crdt = new TreeBasedCRDT();
        setupNetworkHandlers();
    }
    
    /**
     * Initialize the system with the given content
     * 
     * @return A future that completes when initialization is done
     */
    public CompletableFuture<Void> initialize() {
        return collaborationService.requestInitialState()
            .thenAccept(initialContent -> {
                logger.info("Initializing with content length: {}", initialContent.length());
                crdt.initialize(initialContent);
                localContent = initialContent;
                notifyContentChanged();
            });
    }
    
    /**
     * Update the local text content
     * 
     * @param newContent The new text content
     */
    public void updateContent(String newContent) {
        if (newContent.equals(localContent)) {
            return;
        }
        
        logger.debug("Content changed from {} to {} chars", localContent.length(), newContent.length());
        
        // Calculate the operations needed to transform localContent to newContent
        List<TextOperation> operations = DeltaCalculator.calculateOperations(localContent, newContent, crdt);
        
        // Send operations to the collaboration service
        for (TextOperation op : operations) {
            collaborationService.sendOperation(op);
        }
        
        // Update local content
        localContent = newContent;
    }
    
    /**
     * Update the selection state
     * 
     * @param selectionState The new selection state
     */
    public void updateSelection(SelectionState selectionState) {
        this.selectionState = selectionState;
        // Here you could also broadcast selection to other users
    }
    
    /**
     * Set a listener for content changes
     * 
     * @param listener A consumer that accepts the new content
     * @return A registration that can be used to remove the listener
     */
    public Registration setContentChangeListener(Consumer<String> listener) {
        this.onContentChangeListener = listener;
        return () -> this.onContentChangeListener = null;
    }
    
    /**
     * Connect to the collaboration service
     */
    public void connect() {
        collaborationService.connect();
    }
    
    /**
     * Disconnect from the collaboration service
     */
    public void disconnect() {
        if (connectionRegistration != null) {
            connectionRegistration.remove();
        }
        collaborationService.disconnect();
    }
    
    /**
     * Get the current selection state
     */
    public SelectionState getSelectionState() {
        return selectionState;
    }
    
    /**
     * Get the current content from the CRDT
     * 
     * @return The current text content
     */
    public String getContentFromCRDT() {
        return crdt.getContent();
    }
    
    /**
     * Set up handlers for network events
     */
    private void setupNetworkHandlers() {
        collaborationService.subscribeToChanges(this::handleRemoteOperation);
        connectionRegistration = collaborationService.subscribeToConnectionChanges(this::handleConnectionChange);
    }
    
    /**
     * Handle connection state changes
     */
    private void handleConnectionChange() {
        boolean connected = collaborationService.isConnected();
        logger.info("Connection state changed: {}", connected ? "connected" : "disconnected");
        // You could update UI or take other actions based on connection state
    }
    
    /**
     * Handle a remote operation
     * 
     * @param operation The operation to handle
     */
    private void handleRemoteOperation(TextOperation operation) {
        logger.debug("Received remote operation: {}", operation);
        
        // Apply the operation to the CRDT
        crdt.applyRemote(operation);
        
        // Update local content
        String newContent = crdt.getContent();
        if (!newContent.equals(localContent)) {
            localContent = newContent;
            notifyContentChanged();
        }
        
        // Acknowledge the operation
        crdt.acknowledge(operation.getId());
    }
    
    /**
     * Notify the content change listener
     */
    private void notifyContentChanged() {
        if (onContentChangeListener != null) {
            onContentChangeListener.accept(localContent);
        }
    }
}