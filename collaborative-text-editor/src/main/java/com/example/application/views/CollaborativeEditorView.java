package com.example.application.views;

import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.richtexteditor.RichTextEditor;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

@Route("editor")
@PageTitle("Collaborative Editor")
public class CollaborativeEditorView extends VerticalLayout implements HasUrlParameter<String> {

    private static final Logger logger = LoggerFactory.getLogger(CollaborativeEditorView.class);

    private final RichTextEditor editor;
    private final WebSocketService webSocketService;
    private final UI ui;
    private final Div userPresenceContainer;
    private final Map<String, Span> userBadges = new HashMap<>();
    private final AtomicBoolean isApplyingRemoteChanges = new AtomicBoolean(false);
    private final CRDTClientDocument crdtDocument = new CRDTClientDocument();

    private String documentId;
    private final String userId;

    public CollaborativeEditorView(WebSocketService webSocketService) {
        this.webSocketService = webSocketService;
        this.userId = generateUserId();
        this.ui = UI.getCurrent();
        logger.info("Initializing CollaborativeEditorView for user {}", userId);

        editor = new RichTextEditor();
        editor.setHeight("500px");
        editor.setWidthFull();

        userPresenceContainer = new Div();
        userPresenceContainer.addClassName("user-presence-container");

        add(userPresenceContainer, editor);
        setPadding(true);
        setSpacing(true);
        setSizeFull();

        editor.addValueChangeListener(e -> {
            if (!isApplyingRemoteChanges.get() && e.getValue() != null) {
                try {
                    Operation operation = new Operation.Builder()
                        .type(Operation.Type.UPDATE)
                        .userId(userId)
                        .content(e.getValue()) // Full content (for now, until fine-grained CRDT used)
                        .build();
        
                    crdtDocument.apply(operation); // apply locally
        
                    ui.setPollInterval(300);
                    logger.info("Sending operation: {}", operation);
                    webSocketService.sendOperation(operation);
                } catch (Exception ex) {
                    logger.error("Error creating operation", ex);
                }
            }
        });
        

    }

    @Override
    public void setParameter(BeforeEvent event, String parameter) {
        this.documentId = parameter;
        logger.info("Connecting user {} to document {}", userId, documentId);

        webSocketService.connect(documentId, userId,
                this::handleOperationReceived,
                this::handleCursorMoved,
                this::handleUserJoined,
                this::handleUserLeft);

        webSocketService.setSyncCallback(content -> ui.access(() -> {
            crdtDocument.setInitialContent(content);
            isApplyingRemoteChanges.set(true);
            editor.setValue(content);
            isApplyingRemoteChanges.set(false);
        }));

        Notification.show("Connected to document: " + documentId);
    }

    private void handleOperationReceived(Operation operation) {
        ui.access(() -> {
            if (operation.getType() == Operation.Type.UPDATE) {
                crdtDocument.apply(operation);
                isApplyingRemoteChanges.set(true);
                editor.setValue(crdtDocument.getContent());
                isApplyingRemoteChanges.set(false);
            }
        });
    }
    

    private void handleCursorMoved(Object position) {
        logger.info("Cursor moved (not implemented): {}", position);
    }

    private void handleUserJoined(String joinedUserId) {
        ui.access(() -> {
            logger.info("User {} joined", joinedUserId);
            if (!joinedUserId.equals(userId) && !userBadges.containsKey(joinedUserId)) {
                Span badge = new Span(joinedUserId);
                badge.getStyle().setBackgroundColor("lightblue");
                userPresenceContainer.add(badge);
                userBadges.put(joinedUserId, badge);
                Notification.show(joinedUserId + " joined the document");
            }
        });
    }

    private void handleUserLeft(String leftUserId) {
        ui.access(() -> {
            logger.info("User {} left", leftUserId);
            Span badge = userBadges.remove(leftUserId);
            if (badge != null) {
                userPresenceContainer.remove(badge);
            }
            Notification.show(leftUserId + " left the document");
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
        webSocketService.disconnect();
    }
}