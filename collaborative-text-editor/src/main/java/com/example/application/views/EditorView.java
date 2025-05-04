package com.example.application.views;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompHeaders;
import com.example.services.WebSocketClientService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.component.html.Span;
import java.lang.reflect.Type;
@Route("editor")
public class EditorView extends HorizontalLayout {

    private VerticalLayout userListLayout = new VerticalLayout();
    private final List<String> COLORS = List.of("red", "blue", "green", "yellow");
    private final Map<String, String> userColorMap = new HashMap<>();

    public EditorView() {
        setupUserSidebar();

        // Editor layout (middle area)
        VerticalLayout editorLayout = new VerticalLayout(); 

        // ✅ Add visible test content
        editorLayout.add(new Span("✅ Editor loaded successfully."));
        editorLayout.add(new Span("Waiting for WebSocket connection..."));

        String documentId = "abc123";
        try {
            StompSession stompSession = new WebSocketClientService().createSession();
            editorLayout.add(new Span("✅ WebSocket connected."));
            subscribeToUserUpdates(documentId, stompSession);
        } catch (Exception e) {
            editorLayout.add(new Span("❌ WebSocket connection failed: " + e.getMessage()));
        }

        add(userListLayout, editorLayout);
    }

    private void setupUserSidebar() {
        userListLayout.setWidth("200px");
        userListLayout.setPadding(true);
        userListLayout.setSpacing(true);
        userListLayout.add(new Span("👥 Users will appear here:"));
    }

    private void subscribeToUserUpdates(String documentId, StompSession stompSession) {
        stompSession.subscribe("/topic/users/" + documentId, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return String[].class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                UI.getCurrent().access(() -> {
                    updateUserList((String[]) payload);
                });
            }
        });
    }

    private void updateUserList(String[] users) {
        userListLayout.removeAll();
        userListLayout.add(new Span("👥 Active Users:"));

        for (int i = 0; i < users.length; i++) {
            String user = users[i];
            String color = COLORS.get(i % COLORS.size());
            userColorMap.put(user, color);

            Span userSpan = new Span(user);
            userSpan.getStyle().set("color", color);
            userListLayout.add(userSpan);
        }
    }
}
