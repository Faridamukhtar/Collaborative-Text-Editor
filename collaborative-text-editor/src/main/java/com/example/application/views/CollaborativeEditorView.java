package com.example.application.views;

import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.richtexteditor.RichTextEditor;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.shared.Registration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

@Route("editor")
@PageTitle("Collaborative Editor")
@CssImport("./styles/editor-styles.css")
public class CollaborativeEditorView extends VerticalLayout implements HasUrlParameter<String> {

    private static final Logger logger = LoggerFactory.getLogger(CollaborativeEditorView.class);

    private final RichTextEditor editor;
    private final WebSocketService webSocketService;
    private final UI ui;
    private final Div userPresenceContainer;
    private final Map<String, UserPresence> userPresences = new HashMap<>();
    private final AtomicBoolean isApplyingRemoteChanges = new AtomicBoolean(false);
    private CRDTClientDocument crdtDocument;
    private String documentId;
    private final String userId;
    private Registration valueChangeRegistration;
    private String lastSentValue = "";
    private final Div statusIndicator;
    private final static int DEBOUNCE_DELAY_MS = 300;
    private long lastLocalChange = 0;

    public CollaborativeEditorView(WebSocketService webSocketService) {
        this.webSocketService = webSocketService;
        this.userId = generateUserId();
        this.ui = UI.getCurrent();
        this.crdtDocument = new CRDTClientDocument(userId);
        logger.info("Initializing CollaborativeEditorView for user {}", userId);

        // Create status indicator
        statusIndicator = new Div();
        statusIndicator.addClassName("status-indicator");
        statusIndicator.setText("Connecting...");
        statusIndicator.getStyle().set("color", "orange");
        
        // Create header with status and controls
        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setAlignItems(Alignment.CENTER);
        
        Span title = new Span("Collaborative Editor");
        title.getStyle().set("font-weight", "bold");
        title.getStyle().set("font-size", "1.2em");
        
        Button reconnectButton = new Button("Reconnect");
        reconnectButton.addClickListener(e -> reconnect());
        
        header.add(title, statusIndicator, reconnectButton);
        header.setFlexGrow(1, title);
        
        // Create editor
        editor = new RichTextEditor();
        editor.setHeight("500px");
        editor.setWidthFull();

        // User presence display area
        userPresenceContainer = new Div();
        userPresenceContainer.addClassName("user-presence-container");
        userPresenceContainer.getStyle().set("display", "flex");
        userPresenceContainer.getStyle().set("gap", "8px");
        userPresenceContainer.getStyle().set("margin-bottom", "10px");

        // Layout
        add(header, userPresenceContainer, editor);
        setPadding(true);
        setSpacing(true);
        setSizeFull();

        // Setup error handling
        webSocketService.setErrorCallback(this::handleError);
    }

    @Override
    public void setParameter(BeforeEvent event, String parameter) {
        this.documentId = parameter;
        logger.info("Connecting user {} to document {}", userId, documentId);

        // Register value change listener
        setupEditorListeners();

        // Connect to WebSocket
        connectToWebSocket();

        Notification.show("Connected to document: " + documentId);
    }

    private void setupEditorListeners() {
        // Remove existing listener if any
        if (valueChangeRegistration != null) {
            valueChangeRegistration.remove();
        }

        // Create new listener with debounce
        valueChangeRegistration = editor.addValueChangeListener(e -> {
            if (!isApplyingRemoteChanges.get() && e.getValue() != null) {
                long now = System.currentTimeMillis();
                
                // Debounce to avoid too many operations
                if (now - lastLocalChange > DEBOUNCE_DELAY_MS) {
                    lastLocalChange = now;
                    processLocalChange(e.getValue());
                } else {
                    // Schedule a change check later
                    ui.setPollInterval(DEBOUNCE_DELAY_MS);
                }
            }
        });
    }

    private void processLocalChange(String newValue) {
        try {
            // Detect changes between previous and current value
            if (!lastSentValue.equals(newValue)) {
                // For simplicity in this example, we'll send the full document
                // A real implementation would use diffing to calculate precise operations
                Operation operation = new Operation.Builder()
                    .type(Operation.Type.UPDATE)
                    .userId(userId)
                    .content(newValue)
                    .timestamp(System.currentTimeMillis())
                    .build();
                
                // Apply locally first
                crdtDocument.apply(operation);
                lastSentValue = newValue;
                
                // Then send to server
                webSocketService.sendOperation(operation);
                logger.info("Sent operation from local change");
            }
        } catch (Exception ex) {
            logger.error("Error processing local change", ex);
            handleError("Failed to process your changes: " + ex.getMessage());
        }
    }

    private void connectToWebSocket() {
        statusIndicator.setText("Connecting...");
        statusIndicator.getStyle().set("color", "orange");
        
        webSocketService.connect(documentId, userId,
                this::handleOperationReceived,
                this::handleCursorMoved,
                this::handleUserJoined,
                this::handleUserLeft);

        webSocketService.setSyncCallback(content -> ui.access(() -> {
            try {
                statusIndicator.setText("Connected");
                statusIndicator.getStyle().set("color", "green");
                
                isApplyingRemoteChanges.set(true);
                crdtDocument.setInitialContent(content);
                lastSentValue = content;
                editor.setValue(content);
                isApplyingRemoteChanges.set(false);
                
                logger.info("Document synchronized with server content");
            } catch (Exception e) {
                logger.error("Error applying sync content", e);
                handleError("Failed to synchronize document: " + e.getMessage());
            }
        }));
    }

    private void reconnect() {
        if (documentId != null) {
            webSocketService.disconnect();
            connectToWebSocket();
        }
    }

    private void handleOperationReceived(Operation operation) {
        ui.access(() -> {
            try {
                if (!operation.getUserId().equals(userId)) {
                    logger.info("Received operation from {}: {}", operation.getUserId(), operation.getType());
                    
                    isApplyingRemoteChanges.set(true);
                    crdtDocument.apply(operation);
                    String newContent = crdtDocument.getContent();
                    lastSentValue = newContent;
                    editor.setValue(newContent);
                    isApplyingRemoteChanges.set(false);
                    
                    // Update user activity indicator
                    updateUserActivity(operation.getUserId());
                }
            } catch (Exception e) {
                logger.error("Error applying remote operation", e);
                handleError("Failed to apply changes from other users: " + e.getMessage());
            }
        });
    }

    private void handleCursorMoved(Object position) {
        // Implement cursor position tracking
        // This would display other users' cursors in the editor
        logger.info("Cursor moved: {}", position);
    }

    private void handleUserJoined(String joinedUserId) {
        ui.access(() -> {
            logger.info("User {} joined", joinedUserId);
            if (!joinedUserId.equals(userId)) {
                // Create or update user presence indicator
                UserPresence presence = userPresences.computeIfAbsent(joinedUserId,
                        id -> new UserPresence(id, userPresenceContainer));
                presence.setOnline(true);
                Notification.show(joinedUserId + " joined the document");
            }
        });
    }

    private void handleUserLeft(String leftUserId) {
        ui.access(() -> {
            logger.info("User {} left", leftUserId);
            UserPresence presence = userPresences.get(leftUserId);
            if (presence != null) {
                presence.setOnline(false);
                Notification.show(leftUserId + " left the document");
            }
        });
    }

    private void updateUserActivity(String userId) {
        UserPresence presence = userPresences.get(userId);
        if (presence != null) {
            presence.showActivity();
        }
    }

    private void handleError(String errorMessage) {
        ui.access(() -> {
            statusIndicator.setText("Disconnected");
            statusIndicator.getStyle().set("color", "red");
            Notification.show("Error: " + errorMessage, 5000, Notification.Position.BOTTOM_CENTER);
        });
    }

    private String generateUserId() {
        Object principal = VaadinSession.getCurrent().getAttribute("user");
        return (principal != null) ? principal.toString() : "user-" + new Random().nextInt(10000);
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        super.onDetach(detachEvent);
        logger.info("Detaching CollaborativeEditorView for user {}", userId);
        if (valueChangeRegistration != null) {
            valueChangeRegistration.remove();
        }
        webSocketService.disconnect();
    }

    /**
     * Class to handle user presence visualization
     */
    private static class UserPresence {
        private final Div badge;
        private final String userId;
        private boolean isOnline = false;

        public UserPresence(String userId, Div container) {
            this.userId = userId;
            this.badge = new Div();
            badge.setText(userId);
            badge.getStyle().set("background-color", generateColorFromUserId(userId));
            badge.getStyle().set("color", "white");
            badge.getStyle().set("padding", "4px 8px");
            badge.getStyle().set("border-radius", "16px");
            badge.getStyle().set("font-size", "14px");
            badge.getStyle().set("transition", "all 0.3s");
            container.add(badge);
        }

        public void setOnline(boolean online) {
            this.isOnline = online;
            if (online) {
                badge.getStyle().set("opacity", "1");
            } else {
                badge.getStyle().set("opacity", "0.5");
            }
        }

        public void showActivity() {
            if (isOnline) {
                // Flash animation to show activity
                badge.getStyle().set("transform", "scale(1.2)");
                UI.getCurrent().getPage().executeJs("setTimeout(() => {" +
                        "$0.style.transform = 'scale(1)';" +
                        "}, 300)", badge.getElement());
            }
        }

        private String generateColorFromUserId(String userId) {
            // Generate a deterministic color from user ID
            int hash = userId.hashCode();
            int r = (hash & 0xFF0000) >> 16;
            int g = (hash & 0x00FF00) >> 8;
            int b = hash & 0x0000FF;
            
            // Ensure color is dark enough for white text
            r = Math.min(r, 150);
            g = Math.min(g, 150);
            b = Math.min(b, 150);
            
            return String.format("#%02x%02x%02x", r, g, b);
        }
    }
}