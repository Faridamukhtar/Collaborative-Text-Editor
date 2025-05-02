package com.example.application.views.pageeditor;

import com.example.application.views.pageeditor.CRDT.*;
import com.example.application.views.pageeditor.socketsLogic.*;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.shared.Registration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A collaborative text editor component that uses CRDT for conflict-free editing
 */
public class CollaborativeEditor extends VerticalLayout {
    private static final Logger logger = LoggerFactory.getLogger(CollaborativeEditor.class);
    
    private final TextArea editor = new TextArea();
    private final Button connectButton = new Button("Connect");
    private final Button disconnectButton = new Button("Disconnect");
    private final Div statusIndicator = new Div();
    
    private final CollaborationService collaborationService;
    private final CollaborationManager collaborationManager;
    private Registration editorValueChangeRegistration;
    private Registration contentChangeRegistration;
    private Registration connectionChangeRegistration;
    
    /**
     * Create a new collaborative editor with a specific service
     * 
     * @param collaborationService The collaboration service to use
     */
    public CollaborativeEditor(CollaborationService collaborationService) {
        this.collaborationService = collaborationService;
        this.collaborationManager = new CollaborationManager(collaborationService);
        
        setupUI();
        setupEventHandlers();
        
        // Initialize with content from the server
        collaborationManager.initialize()
            .thenRun(() -> {
                updateEditorContent(collaborationManager.getContentFromCRDT());
                Notification.show("Document loaded successfully");
            })
            .exceptionally(e -> {
                Notification.show("Failed to load document: " + e.getMessage());
                logger.error("Initialization failed", e);
                return null;
            });
    }
    
    /**
     * Create a new collaborative editor
     * 
     * @param documentId ID of the document to edit
     * @param userId ID of the current user
     */
    public CollaborativeEditor(String documentId, String userId) {
        this(createCollaborationService(documentId, userId));
    }
    
    /**
     * Create a collaboration service
     * 
     * @param documentId ID of the document to edit
     * @param userId ID of the current user
     * @return A collaboration service
     */
    private static CollaborationService createCollaborationService(String documentId, String userId) {
        // For production, use WebSocketCollaborationService
        // For testing, use MockCollaborationService
        
        // Example for WebSocket
        String serverUrl = "wss://your-collaboration-server.com";
        return new WebSocketCollaborationService(serverUrl, documentId, userId);
        
        // Example for Mock
        // return new MockCollaborationService("Initial content for testing");
    }
    
    /**
     * Set up the UI components
     */
    private void setupUI() {
        editor.setWidthFull();
        editor.setHeight("400px");
        editor.setPlaceholder("Start typing to collaborate...");
        
        statusIndicator.setText("Disconnected");
        statusIndicator.getStyle().set("color", "red");
        
        disconnectButton.setEnabled(false);
        
        Div controlsDiv = new Div(connectButton, disconnectButton, statusIndicator);
        controlsDiv.getStyle().set("display", "flex");
        controlsDiv.getStyle().set("gap", "10px");
        controlsDiv.getStyle().set("align-items", "center");
        
        add(editor, controlsDiv);
    }
    
    /**
     * Set up event handlers
     */
    private void setupEventHandlers() {
        // Editor value change
        editorValueChangeRegistration = editor.addValueChangeListener(event -> {
            // Get selection before updating
            int start = editor.getElement().getProperty("selectionStart", 0);
            int end = editor.getElement().getProperty("selectionEnd", 0);
            
            // Update content and selection
            collaborationManager.updateContent(event.getValue());
            collaborationManager.updateSelection(new SelectionState(start, end));
        });
        
        // Connect button
        connectButton.addClickListener(event -> {
            collaborationManager.connect();
        });
        
        // Disconnect button
        disconnectButton.addClickListener(event -> {
            collaborationManager.disconnect();
        });
        
        // Subscribe to content changes from the collaboration manager
        contentChangeRegistration = collaborationManager.setContentChangeListener(this::updateEditorContent);
        
        // Subscribe to connection state changes
        connectionChangeRegistration = collaborationService.subscribeToConnectionChanges(() -> {
            updateConnectionStatus(collaborationService.isConnected());
        });
    }
    
    /**
     * Component attached to the UI
     */
    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        updateConnectionStatus(collaborationService.isConnected());
    }
    
    /**
     * Update the editor content
     * 
     * @param content New content
     */
    private void updateEditorContent(String content) {
        // Log the content update
        logger.info("Updating editor content: {}", content);
        // Temporarily remove value change listener to avoid triggering it
        if (editorValueChangeRegistration != null) {
            editorValueChangeRegistration.remove();
        }
        
        // Update content
        editor.setValue(content);
        
        // Restore value change listener
        editorValueChangeRegistration = editor.addValueChangeListener(event -> {
            int start = editor.getElement().getProperty("selectionStart", 0);
            int end = editor.getElement().getProperty("selectionEnd", 0);
            
            collaborationManager.updateContent(event.getValue());
            collaborationManager.updateSelection(new SelectionState(start, end));
        });
        
        // Restore selection if available
        SelectionState selectionState = collaborationManager.getSelectionState();
        if (selectionState != null) {
            editor.getElement().executeJs(
                "this.setSelectionRange($0, $1)",
                selectionState.getStart(),
                selectionState.getEnd()
            );
        }
    }
    
    /**
     * Update the connection status indicator
     * 
     * @param connected True if connected, false otherwise
     */
    private void updateConnectionStatus(boolean connected) {
        if (connected) {
            statusIndicator.setText("Connected");
            statusIndicator.getStyle().set("color", "green");
            connectButton.setEnabled(false);
            disconnectButton.setEnabled(true);
        } else {
            statusIndicator.setText("Disconnected");
            statusIndicator.getStyle().set("color", "red");
            connectButton.setEnabled(true);
            disconnectButton.setEnabled(false);
        }
    }
    
    /**
     * Clean up resources when component is detached
     */
    @Override
    protected void onDetach(DetachEvent detachEvent) {
        super.onDetach(detachEvent);
        
        if (editorValueChangeRegistration != null) {
            editorValueChangeRegistration.remove();
        }
        if (contentChangeRegistration != null) {
            contentChangeRegistration.remove();
        }
        if (connectionChangeRegistration != null) {
            connectionChangeRegistration.remove();
        }
        collaborationManager.disconnect();
    }
}