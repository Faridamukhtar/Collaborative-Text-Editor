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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;

@Route("/editor")
@JsModule("./js/text-editor-connector.js")
public class CollaborativeTextEditor extends VerticalLayout implements CollaborativeEditUiListener, HasUrlParameter<String> {

    private enum OperationType {
        INSERT, DELETE, BATCH_INSERT, BATCH_DELETE
    }

    private record EditorState(
        OperationType type,
        String text,
        int position,
        String userId,
        String fullContent,
        int cursorPosition
    ) {}

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
    private final Deque<EditorState> undoStack = new ArrayDeque<>();
    private final Deque<EditorState> redoStack = new ArrayDeque<>();
    private boolean isUndoRedoOperation = false;
    private int currentCursorPosition = 0;
    private List<String> active_users;

    @Autowired
    private CollaborativeEditService collaborativeEditService;

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
            editor.setValue(content);
            saveInitialState(content);
        }
        if ("viewer".equals(role) || (!viewCode.isEmpty() && editCode.isEmpty()))
            editor.setReadOnly(true);
        editor.setWidthFull();
        editor.setHeight("100%");
        editor.setLabel("Edit text below - changes are shared with all users");

        editor.addValueChangeListener(event -> {
            if (!suppressInput && !isUndoRedoOperation) {
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

    private void saveInitialState(String content) {
        undoStack.push(new EditorState(
            OperationType.INSERT,
            content,
            0,
            userId,
            content,
            0
        ));
    }

    private void updateCursorPosition() {
        editor.getElement().executeJs("return this.inputElement.selectionStart")
                .then(Integer.class, (SerializableConsumer<Integer>) pos -> currentCursorPosition = pos);
        onCursorLineChanged(currentCursorPosition);
    }

    private void initializeConnector() {
        getElement().executeJs("window.initEditorConnector($0, $1)", getElement(), userId);
    }

    @ClientCallable
    public void onCharacterInserted(String character, int position) {
        if (suppressInput) return;
        
        saveStateToUndoStack(
            OperationType.INSERT,
            character,
            position,
            editor.getValue()
        );
        
        ClientEditRequest req = CollaborativeEditService.createInsertRequest(
            character, position, userId, documentId
        );
        collaborativeEditService.sendEditRequest(req);
    }

    @ClientCallable
    public void onCharacterDeleted(int position) {
        System.out.println("onCharacterDeleted called with position: " + position);
        if (suppressInput) return;
        System.out.println("Deleting character at position: " + position);
        // 👇 Capture the editor content BEFORE deletion happens
        String currentText = editor.getValue();
        
        if (position >= 0 && position < currentText.length()) {
            String deletedChar = currentText.substring(position, position + 1);

            // Save full state BEFORE the deletion
            saveStateToUndoStack(
                OperationType.DELETE,
                deletedChar,
                position,
                currentText
            );

            // Now send deletion request
            ClientEditRequest req = CollaborativeEditService.createDeleteRequest(
                position, userId, documentId
            );
            collaborativeEditService.sendEditRequest(req);
        }
    }


    @ClientCallable
    public void onCharacterBatchInserted(String text, int position) {
        System.out.println("onCharacterBatchInserted called with text: " + text + ", position: " + position);
        if (suppressInput || text.isEmpty()) return;
        
        if(!isUndoRedoOperation) {
            saveStateToUndoStack(
                OperationType.BATCH_INSERT,
                text,
                position,
                editor.getValue()
            );
        }
        
        for (int i = 0; i < text.length(); i++) {
            ClientEditRequest req = CollaborativeEditService.createInsertRequest(
                String.valueOf(text.charAt(i)), 
                position, 
                userId, 
                documentId
            );
            collaborativeEditService.sendEditRequest(req);
        }
    }

    @ClientCallable
    public void onCharacterBatchDeleted(int startPosition, int count) {
        System.out.println("onCharacterBatchDeleted called with startPosition: " + startPosition + ", count: " + count);
        if (suppressInput || count <= 0) return;

        String currentText = editor.getValue();
        System.out.println("Current text: " + currentText.length() + ", startPosition: " + startPosition + ", count: " + count);
        if (startPosition >= 0 && startPosition + count <= currentText.length()) {
            System.out.println("Deleting characters from position: " + startPosition + " to " + (startPosition + count));
            String deletedText = currentText.substring(startPosition, startPosition + count);

            if(!isUndoRedoOperation) {
                saveStateToUndoStack(
                    OperationType.BATCH_DELETE,
                    deletedText,
                    startPosition,
                    currentText
                );
            }
            for (int i = 0; i < count; i++) {
                ClientEditRequest req = CollaborativeEditService.createDeleteRequest(
                    startPosition, userId, documentId
                );
                System.out.println("Sending delete request for position: " + req);
                collaborativeEditService.sendEditRequest(req);
            }
        }
    }

    private void saveStateToUndoStack(OperationType type, String text, int position, String fullContent) {
        EditorState state = new EditorState(
            type,
            text,
            position,
            userId,
            fullContent,
            currentCursorPosition
        );
        undoStack.push(state);
        redoStack.clear();
    }

    private void undo() {
        System.out.println("Undoing last operation...");
        if (undoStack.isEmpty()) return;
        
        EditorState lastState = undoStack.pop();
        System.out.println("Last state: " + lastState);
        if (!userId.equals(lastState.userId())) {
            undo();
            return;
        }
        isUndoRedoOperation = true;
        System.out.println("Undoing operation of type: " + lastState.type());
        try {
            String current = editor.getValue();
            switch (lastState.type()) {
                case INSERT -> {
                    System.out.println("Undoing INSERT operation");
                    int pos = lastState.position();
                    onCharacterDeleted(pos);
                    if (pos >= 0 && pos < current.length() && 
                        current.startsWith(lastState.text(), pos)) {
                        editor.setValue(
                            current.substring(0, pos) + 
                            current.substring(pos + lastState.text().length())
                        );
                        currentCursorPosition = pos;
                    }
                }
                case DELETE -> {
                    int pos = lastState.position();
                    onCharacterInserted(lastState.text(), pos);
                    editor.setValue(
                        current.substring(0, pos) +
                        lastState.text() +
                        current.substring(pos)
                    );
                    currentCursorPosition = pos + lastState.text().length();
                }
                case BATCH_INSERT -> {
                    int pos = lastState.position();
                    String inserted = lastState.text();
                    System.out.println("Undoing INSERT operation");
                    onCharacterBatchDeleted(pos, inserted.length());
                    if (pos >= 0 && pos + inserted.length() <= current.length() && 
                        current.startsWith(inserted, pos)) {
                        editor.setValue(
                            current.substring(0, pos) + 
                            current.substring(pos + inserted.length())
                        );
                        currentCursorPosition = pos;
                    }
                }
                case BATCH_DELETE -> {
                    int pos = lastState.position();
                    onCharacterBatchInserted(lastState.text(), pos);
                    editor.setValue(
                        current.substring(0, pos) +
                        lastState.text() +
                        current.substring(pos)
                    );
                    currentCursorPosition = pos + lastState.text().length();
                }
            }
            
            ui.getPage().executeJs(
                "setTimeout(() => { const el = $0.inputElement; el.selectionStart = $1; el.selectionEnd = $1; }, 10)",
                editor.getElement(), currentCursorPosition
            );
            
            redoStack.push(lastState);
        } finally {
            isUndoRedoOperation = false;
        }
    }

    private void redo() {
        if (redoStack.isEmpty()) return;
        
        EditorState nextState = redoStack.pop();
        isUndoRedoOperation = true;
        try {
            String current = editor.getValue();
            switch (nextState.type()) {
                case INSERT -> {
                    int pos = nextState.position();
                    onCharacterInserted(nextState.text(), pos);
                    editor.setValue(
                        current.substring(0, pos) +
                        nextState.text() +
                        current.substring(pos)
                    );
                    currentCursorPosition = pos + nextState.text().length();
                }
                case DELETE -> {
                    int pos = nextState.position();
                    onCharacterDeleted(pos);
                    if (pos >= 0 && pos < current.length() && 
                        current.startsWith(nextState.text(), pos)) {
                        editor.setValue(
                            current.substring(0, pos) + 
                            current.substring(pos + nextState.text().length())
                        );
                        currentCursorPosition = pos;
                    }
                }
                case BATCH_INSERT -> {
                    int pos = nextState.position();
                    onCharacterBatchDeleted(pos, nextState.text().length());
                    editor.setValue(
                        current.substring(0, pos) +
                        nextState.text() +
                        current.substring(pos)
                    );
                    currentCursorPosition = pos + nextState.text().length();
                }
                case BATCH_DELETE -> {
                    int pos = nextState.position();
                    String toDelete = nextState.text();
                    onCharacterBatchInserted(toDelete, pos);
                    if (pos >= 0 && pos + toDelete.length() <= current.length() && 
                        current.startsWith(toDelete, pos)) {
                        editor.setValue(
                            current.substring(0, pos) + 
                            current.substring(pos + toDelete.length())
                        );
                        currentCursorPosition = pos;
                    }
                }
            }
            
            ui.getPage().executeJs(
                "setTimeout(() => { const el = $0.inputElement; el.selectionStart = $1; el.selectionEnd = $1; }, 10)",
                editor.getElement(), currentCursorPosition
            );
            
            undoStack.push(nextState);
        } finally {
            isUndoRedoOperation = false;
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
                active_users=usernames;
                updateActiveUserListUI(usernames);
            } catch (Exception e) {
                System.err.println("\u274C Failed to parse ACTIVE_USERS: " + e.getMessage());
            }
            
            return;
        }
        else if (text.trim().startsWith("{") && text.contains("\"type\":\"CURSOR_UPDATE\"")) {
            System.out.println("yaraaaaaaaab");
            System.out.println(text);
            try {
                JsonObject json = JsonParser.parseString(text).getAsJsonObject();
                JsonObject cursors = json.getAsJsonObject("cursors");
                Map<String, Integer> cursorMap = new HashMap<>();
                System.out.printf("cursors" , cursorMap);
        
                for (String key : cursors.keySet()) {
                    int pos = cursors.get(key).getAsInt();
                    cursorMap.put(key, pos);
                }
        
                updateActiveUserCursorUI(cursorMap);
            } catch (Exception e) {
                System.err.println("❌ Failed to parse CURSOR_UPDATE: " + e.getMessage());
            }
            return;
        }
    

        ui.access(() -> {
            ui.getPage().executeJs("window.suppressInputStart()");
            suppressInput = true;
            editor.setValue(text);
            suppressInput = false;
            ui.getPage().executeJs("window.suppressInputEnd()");
        });
    }

    private void updateActiveUserListUI(List<String> usernames) {
        active_users = usernames;
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

    private void updateActiveUserCursorUI(Map<String, Integer> userCursors) {
        ui.access(() -> {
            // Remove old cursor elements
            activeUserListSection.getChildren()
                .filter(component -> "cursor".equals(component.getElement().getProperty("data-type")))
                .forEach(activeUserListSection::remove);
    
            // Add new header
            Div header = new Div("📍 Cursors:");
            header.getElement().setProperty("data-type", "cursor");
            header.getStyle().set("margin-top", "1rem")
                             .set("margin-bottom", "0.5rem")
                             .set("font-weight", "bold");
            activeUserListSection.add(header);
    
            // Add each user's cursor position
            userCursors.forEach((user, pos) -> {
                if (active_users.contains(user)) {
                    String label = user.equals(userId) ? user + " (you)" : user;
                    Div userDiv = new Div("• " + label + " → Pos: " + pos);
                    userDiv.getElement().setProperty("data-type", "cursor");
                    userDiv.getStyle().set("margin-left", "1rem").set("color", "blue");
                    activeUserListSection.add(userDiv);
                }
            });
        });
    }
    

    private void updateExportResource(String htmlContent) {
        String plainText = helpers.htmlToPlainText(htmlContent);
        StreamResource resource = new StreamResource("document.txt",
                () -> new ByteArrayInputStream(plainText.getBytes(StandardCharsets.UTF_8)));
        hiddenDownloadLink.setHref(resource);
    }

    @ClientCallable
    public void onCursorLineChanged(int lineNumber) {
        System.out.printf("user ", userId , "doc " , documentId , "position", lineNumber);
        ClientEditRequest req = CollaborativeEditService.updateUserCursorLine(lineNumber, userId, documentId);
        collaborativeEditService.sendEditRequest(req);
    }

}