package com.example.application.views;

import com.example.application.connections.CRDT.*;
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
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

@Route("editor")
@JsModule("./js/text-editor-connector.js")
public class CollaborativeTextEditor extends VerticalLayout implements CollaborativeEditUiListener {
    private final UI ui;
    private final TextArea editor;
    private final String userId;

    @Autowired
    private CollaborativeEditService collaborativeEditService;

    public CollaborativeTextEditor() {
        this.ui = UI.getCurrent();
        this.userId = UUID.randomUUID().toString();

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        H2 title = new H2("Live Collaborative Editor");
        add(title);

        editor = new TextArea();
        editor.setWidthFull();
        editor.setHeight("400px");
        editor.setLabel("Edit text below - changes are shared with all users");
        add(editor);

        Div userInfo = new Div(new Text("Your User ID: " + userId));
        userInfo.getStyle().set("margin-top", "10px");
        add(userInfo);

        Button resetButton = new Button("Reset Editor", e -> {
            editor.setValue("");
            Notification.show("Editor content reset by " + userId);
        });
        add(resetButton);

        // 👇 Register this UI to receive CRDT updates from backend
        CollaborativeEditService.setUiListener(this);

        initializeConnector();
    }

    private void initializeConnector() {
        getElement().executeJs("window.initEditorConnector($0, $1)", getElement(), userId);
    }

    @ClientCallable
    public void onCharacterInserted(String character, int position) {
        ClientEditRequest req = CollaborativeEditService.createInsertRequest(character, position, userId);
        collaborativeEditService.sendEditRequest(req);
    }

    @ClientCallable
    public void onCharacterDeleted(int position) {
        ClientEditRequest req = CollaborativeEditService.createDeleteRequest(position, userId);
        collaborativeEditService.sendEditRequest(req);
    }

    @ClientCallable
    public void onCharacterBatchInserted(String text, int position) {
        for (int i = 0; i < text.length(); i++) {
            String ch = String.valueOf(text.charAt(i));
            onCharacterInserted(ch, position + i);
        }
    }

    @ClientCallable
    public void onCharacterBatchDeleted(int startPosition, int count) {
        for (int i = 0; i < count; i++) {
            onCharacterDeleted(startPosition);
        }
    }

    @Override
    public void onServerMessage(String text) {
        ui.access(() -> editor.setValue(text));
    }

    public void updateTextArea(String text) {
        editor.setValue(text);
    }
}
