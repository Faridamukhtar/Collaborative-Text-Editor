package com.example.application.views;

import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.Command;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Route("editor")
@JsModule("./js/text-editor-connector.js")
public class CollaborativeTextEditor extends VerticalLayout {
    private final UI ui;
    private final TextArea editor;
    private final String userId;

    // Store for active users
    private static final ConcurrentHashMap<String, UI> activeUsers = new ConcurrentHashMap<>();

    public CollaborativeTextEditor() {
        this.ui = UI.getCurrent();
        this.userId = UUID.randomUUID().toString();

        // Register this user
        activeUsers.put(userId, ui);

        // Clean up on UI detach
        ui.addDetachListener(event -> activeUsers.remove(userId));

        // Set up the layout
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        // Header
        H2 title = new H2("Live Collaborative Editor");
        add(title);

        // Editor
        editor = new TextArea();
        editor.setWidthFull();
        editor.setHeight("400px");
        editor.setLabel("Edit text below - changes are shared with all users");
        add(editor);

        // User info
        Div userInfo = new Div(new Text("Your User ID: " + userId));
        userInfo.getStyle().set("margin-top", "10px");
        add(userInfo);

        // Connection status
        Div connectionStatus = new Div();
        connectionStatus.setText("Connected Users: " + activeUsers.size());
        connectionStatus.getStyle().set("color", "green");
        add(connectionStatus);

        // Reset button
        Button resetButton = new Button("Reset Editor", e -> {
            editor.setValue("");
            broadcastToAll("reset", "", 0);
            Notification.show("Editor content reset by " + userId);
        });
        add(resetButton);

        // Initialize JavaScript connector
        initializeConnector();
    }

    private void initializeConnector() {
        getElement().executeJs("window.initEditorConnector($0, $1)", getElement(), userId);
    }

    @ClientCallable
    public void onCharacterInserted(String character, int position) {
        System.out.println("[Insert] User " + userId + " inserted '" + character + "' at pos: " + position);
        broadcastToAll("insert", character, position);
    }

    @ClientCallable
    public void onCharacterDeleted(int position) {
        System.out.println("[Delete] User " + userId + " deleted at pos: " + position);
        broadcastToAll("delete", "", position);
    }

    @ClientCallable
    public void onCharacterBatchInserted(String text, int position) {
        System.out.println("[Batch Insert] '" + text + "' at pos: " + position);
        for (int i = 0; i < text.length(); i++) {
            String ch = String.valueOf(text.charAt(i));
            broadcastToAll("insert", ch, position + i);
        }
    }

    @ClientCallable
    public void onCharacterBatchDeleted(int startPosition, int count) {
        System.out.println("[Batch Delete] " + count + " chars from pos: " + startPosition);
        for (int i = 0; i < count; i++) {
            broadcastToAll("delete", "", startPosition);
        }
    }

    private void broadcastToAll(String operation, String character, int position) {
        activeUsers.forEach((id, userUI) -> {
            if (!id.equals(userId)) {
                userUI.access((Command) () -> {
                    userUI.getPage().executeJs(
                        "window.applyRemoteChange($0, $1, $2, $3)",
                        operation, character, position, userId
                    );
                });
            }
        });
    }
} 
