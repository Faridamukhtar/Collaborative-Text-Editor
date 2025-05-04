package com.example.application.views;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.component.html.Label;
import com.vaadin.flow.component.html.Span;
import java.lang.reflect.Type;
import io.netty.handler.codec.stomp.StompHeaders;

@Route("editor")
public class EditorView extends HorizontalLayout {

    private VerticalLayout userListLayout = new VerticalLayout();
    private final List<String> COLORS = List.of("red", "blue", "green", "yellow");
    private final Map<String, String> userColorMap = new HashMap<>();

    public EditorView() {
        setupUserSidebar();
        VerticalLayout editorLayout = new VerticalLayout(); // Your text area, buttons, etc.
        // // Simulated values
        String documentId = "abc123"; // get from URL, backend, or session
        // StompSession stompSession = createOrGetStompSession(); // your own method

        // subscribeToUserUpdates(documentId, stompSession);
        add(userListLayout, editorLayout);
    }

    private void setupUserSidebar() {
        userListLayout.setWidth("200px");
        userListLayout.setPadding(true);
        userListLayout.setSpacing(true);
    }

  // private void subscribeToUserUpdates(String documentId, StompSession stompSession) {
  //   stompSession.subscribe("/topic/users/" + documentId, new StompFrameHandler() {
  //       @Override
  //       public Type getPayloadType(StompHeaders headers) {
  //           return String[].class; // Or List<String> depending on your backend
  //       }

  //       @Override
  //       public void handleFrame(StompHeaders headers, Object payload) {
  //           UI.getCurrent().access(() -> {
  //               updateUserList((String[]) payload);
  //           });
  //       }
  //   });
  // }

  private void updateUserList(String[] users) {
    userListLayout.removeAll();
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

