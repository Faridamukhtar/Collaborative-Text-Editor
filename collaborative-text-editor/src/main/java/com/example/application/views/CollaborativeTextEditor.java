package com.example.application.views;

import com.example.application.connections.CRDT.*;
import com.example.application.views.components.SidebarUtil;
import com.example.application.views.components.helpers;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Section;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.function.SerializableConsumer;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;
import com.vaadin.flow.server.VaadinSession;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Route("/editor")
@JsModule("./js/text-editor-connector.js")
public class CollaborativeTextEditor extends VerticalLayout implements CollaborativeEditUiListener, HasUrlParameter<String> {

    private UI ui;
    private TextArea editor;
    private String userId;
    private boolean suppressInput = false;
    private String documentId;
    private String viewCode;
    private String editCode;
    private String role;
    private Anchor hiddenDownloadLink;
    private Div activeUserListSection = SidebarUtil.createActiveUserListSection();

    private static final Map<String, UI> activeUsers = new ConcurrentHashMap<>();

    private final Deque<EditorState> undoStack = new ArrayDeque<>(5);
    private final Deque<EditorState> redoStack = new ArrayDeque<>(5);
    private boolean isUndoRedoOperation = false;
    private int currentCursorPosition = 0;

    @Autowired
    private CollaborativeEditService collaborativeEditService;

    private record EditorState(String content, int cursorPosition) {}

    public CollaborativeTextEditor() {
        setSizeFull();
    }

    @Override
    public void setParameter(BeforeEvent event, String parameter) {
        userId = helpers.extractData(parameter, "userId");
        role = helpers.extractData(parameter, "role");
        documentId = helpers.extractData(parameter, "documentId");
        viewCode = helpers.extractData(parameter, "viewCode");
        editCode = helpers.extractData(parameter, "editCode");
        this.ui = UI.getCurrent();

        collaborativeEditService.registerListener(documentId, userId, this);
        collaborativeEditService.connectWebSocket(documentId, userId);
        ui.addDetachListener(e -> collaborativeEditService.unregisterListener(documentId, userId));

        initializeEditorUi();
    }

    private void initializeEditorUi() {
        String content = (String) VaadinSession.getCurrent().getAttribute("importedText");
        activeUsers.put(userId, ui);
        ui.addDetachListener(event -> activeUsers.remove(userId));

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        H2 title = new H2("Live Collaborative Editor");
        title.addClassName("header");
        Div header = new Div(title);
        header.getStyle().set("text-align", "left");

        editor = new TextArea();
        if (content != null) {
            onCharacterBatchInserted(content, 0);
            editor.setValue(content);
            saveStateToUndoStack(content, 0);
        }
        if ("viewer".equals(role) || (!viewCode.isEmpty() && editCode.isEmpty()))
            editor.setReadOnly(true);
        editor.setWidthFull();
        editor.setHeight("100%");
        editor.setLabel("Edit text below - changes are shared with all users");

        editor.addValueChangeListener(event -> {
            if (!suppressInput && !isUndoRedoOperation) {
                if (undoStack.isEmpty() || !undoStack.peek().content().equals(event.getValue())) {
                    saveStateToUndoStack(event.getValue(), currentCursorPosition);
                    redoStack.clear();
                }
                updateExportResource(event.getValue());
            }
        });

        editor.getElement().addEventListener("keyup", e -> updateCursorPosition());
        editor.getElement().addEventListener("click", e -> updateCursorPosition());

        HorizontalLayout editorToolbar = new HorizontalLayout();
        editorToolbar.setWidthFull();
        editorToolbar.setPadding(true);
        editorToolbar.setSpacing(true);
        editorToolbar.getStyle()
                .set("border-bottom", "1px solid var(--lumo-contrast-10pct)")
                .set("background-color", "var(--lumo-contrast-5pct)");

        Button undoButton = new Button("Undo", VaadinIcon.ARROW_BACKWARD.create());
        undoButton.addClickListener(e -> undo());
        Button redoButton = new Button("Redo", VaadinIcon.ARROW_FORWARD.create());
        redoButton.addClickListener(e -> redo());
        Button exportButton = new Button("Export", VaadinIcon.DOWNLOAD.create());
        exportButton.addClickListener(e -> UI.getCurrent().getPage().executeJs("document.getElementById('hiddenDownloadLink').click();"));

        editorToolbar.add(undoButton, redoButton);
        editorToolbar.addAndExpand(new Div());
        editorToolbar.add(exportButton);

        VerticalLayout editorContainer = new VerticalLayout();
        editorContainer.setSizeFull();
        editorContainer.setPadding(false);
        editorContainer.setSpacing(false);
        editorContainer.add(editorToolbar, editor);
        editorContainer.setFlexGrow(1, editor);

        hiddenDownloadLink = new Anchor();
        hiddenDownloadLink.setId("hiddenDownloadLink");
        hiddenDownloadLink.getStyle().set("display", "none");
        hiddenDownloadLink.getElement().setAttribute("download", true);

        Section sidebar = SidebarUtil.createSidebar(viewCode, editCode, userId, hiddenDownloadLink, activeUserListSection);

        HorizontalLayout mainLayout = new HorizontalLayout(editorContainer, sidebar);
        mainLayout.setSizeFull();
        mainLayout.setFlexGrow(3, editorContainer);
        mainLayout.setFlexGrow(1, sidebar);

        add(mainLayout);
        initializeConnector();
    }

    private void updateCursorPosition() {
        editor.getElement().executeJs("return this.inputElement.selectionStart")
                .then(Integer.class, (SerializableConsumer<Integer>) pos -> currentCursorPosition = pos);
    }

    private void initializeConnector() {
        getElement().executeJs("window.initEditorConnector($0, $1)", getElement(), userId);
    }

    private void saveStateToUndoStack(String content, int cursorPosition) {
        if (undoStack.size() >= 5) undoStack.removeFirst();
        undoStack.push(new EditorState(content, cursorPosition));
    }

    private void saveStateToRedoStack(String content, int cursorPosition) {
        if (redoStack.size() >= 5) redoStack.removeFirst();
        redoStack.push(new EditorState(content, cursorPosition));
    }

    private void undo() {
        if (undoStack.size() > 1) {
            saveStateToRedoStack(editor.getValue(), currentCursorPosition);
            undoStack.pop();
            applyState(undoStack.peek());
        }
    }

    private void redo() {
        if (!redoStack.isEmpty()) {
            saveStateToUndoStack(editor.getValue(), currentCursorPosition);
            applyState(redoStack.pop());
        }
    }

    private void applyState(EditorState state) {
        isUndoRedoOperation = true;
        suppressInput = true;
        ui.access(() -> {
            try {
                editor.setValue(state.content());
                ui.getPage().executeJs(
                        "setTimeout(() => { const el = $0.inputElement; el.selectionStart = $1; el.selectionEnd = $1; }, 10)",
                        editor.getElement(), state.cursorPosition());
                currentCursorPosition = state.cursorPosition();
            } finally {
                suppressInput = false;
                isUndoRedoOperation = false;
            }
        });
    }

    @ClientCallable
    public void onCharacterInserted(String character, int position) {
        if (suppressInput) return;
        ClientEditRequest req = CollaborativeEditService.createInsertRequest(character, position, userId, documentId);
        collaborativeEditService.sendEditRequest(req);
    }

    @ClientCallable
    public void onCharacterDeleted(int position) {
        if (suppressInput) return;
        ClientEditRequest req = CollaborativeEditService.createDeleteRequest(position, userId, documentId);
        collaborativeEditService.sendEditRequest(req);
    }

    @ClientCallable
    public void onCharacterBatchInserted(String text, int position) {
        if (suppressInput) return;
        for (int i = 0; i < text.length(); i++) {
            onCharacterInserted(String.valueOf(text.charAt(i)), position + i);
        }
    }

    @ClientCallable
    public void onCharacterBatchDeleted(int startPosition, int count) {
        if (suppressInput) return;
        for (int i = 0; i < count; i++) {
            onCharacterDeleted(startPosition);
        }
    }

    @Override
    public void onServerMessage(String text) {
        if (text.trim().startsWith("{") && text.contains("\"type\":\"ACTIVE_USERS\"")) {
            try {
                JsonObject json = JsonParser.parseString(text).getAsJsonObject();
                JsonArray users = json.getAsJsonArray("usernames");
                List<String> usernames = new ArrayList<>();
                for (int i = 0; i < users.size(); i++) {
                    usernames.add(users.get(i).getAsString());
                }
                updateActiveUserListUI(usernames);
            } catch (Exception e) {
                System.err.println("\u274C Failed to parse ACTIVE_USERS: " + e.getMessage());
            }
            return;
        }

        ui.access(() -> {
            ui.getPage().executeJs("window.suppressInputStart()");
            suppressInput = true;
            if (!text.contains("\"type\":\"reconnectFailed\"") && !text.contains("\"type\":\"missedOperations\"")) {
                editor.setValue(text);
                saveStateToUndoStack(text, currentCursorPosition);
            }            
            saveStateToUndoStack(text, currentCursorPosition);
            suppressInput = false;
            ui.getPage().executeJs("window.suppressInputEnd()");
        });
    }

    @Override
    public void onReconnectionFailed() {
        ui.access(() -> {
            com.vaadin.flow.component.notification.Notification.show(
                "Reconnection failed. Please refresh the page to rejoin the session.", 5000,
                com.vaadin.flow.component.notification.Notification.Position.MIDDLE
            );
        });
    }

    private void updateActiveUserListUI(List<String> usernames) {
        ui.access(() -> {
            activeUserListSection.removeAll();
            Div header = new Div("\uD83D\uDFE2 Active Users (" + usernames.size() + "):");
            header.getStyle().set("margin-bottom", "0.5rem").set("font-weight", "bold");
            activeUserListSection.add(header);
            for (String user : usernames) {
                Div userDiv = new Div("\u2022 " + user);
                userDiv.getStyle().set("margin-left", "1rem").set("color", "green");
                activeUserListSection.add(userDiv);
            }
        });
    }

    private void updateExportResource(String htmlContent) {
        String plainText = helpers.htmlToPlainText(htmlContent);
        StreamResource resource = new StreamResource("document.txt",
                () -> new ByteArrayInputStream(plainText.getBytes(StandardCharsets.UTF_8)));
        hiddenDownloadLink.setHref(resource);
    }
}