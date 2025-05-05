package com.example.application.views;

import com.example.application.connections.CRDT.*;
import com.example.application.views.components.SidebarUtil;
import com.example.application.views.components.helpers;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import elemental.json.JsonValue;
import com.google.gson.JsonElement;
import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Section;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
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
    private final VerticalLayout commentPanel = new VerticalLayout();

    private static final Map<String, UI> activeUsers = new ConcurrentHashMap<>();
    private final Deque<EditorState> undoStack = new ArrayDeque<>();
    private final Deque<EditorState> redoStack = new ArrayDeque<>();
    private boolean isUndoRedoOperation = false;
    private int currentCursorPosition = 0;

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
        editor.setHeight("400px");
        editor.setLabel("Edit text below - changes are shared with all users");
    
        editor.addValueChangeListener(event -> {
            if (!suppressInput && !isUndoRedoOperation) {
                updateExportResource(event.getValue());
            }
        });
    
        editor.getElement().addEventListener("keyup", e -> updateCursorPosition());
        editor.getElement().addEventListener("click", e -> updateCursorPosition());
    
        // Toolbar
        Button undoButton = new Button("Undo", VaadinIcon.ARROW_BACKWARD.create());
        undoButton.addClickListener(e -> undo());
    
        Button redoButton = new Button("Redo", VaadinIcon.ARROW_FORWARD.create());
        redoButton.addClickListener(e -> redo());
    
        Button exportButton = new Button("Export", VaadinIcon.DOWNLOAD.create());
        exportButton.addClickListener(e ->
            UI.getCurrent().getPage().executeJs("document.getElementById('hiddenDownloadLink').click();")
        );
    
        Button commentButton = new Button("Comment", VaadinIcon.COMMENT.create());
        commentButton.addClickListener(e -> addCommentBox());
    
        HorizontalLayout editorToolbar = new HorizontalLayout(
            commentButton, undoButton, redoButton, new Div(), exportButton
        );
        editorToolbar.setWidthFull();
        editorToolbar.setPadding(true);
        editorToolbar.setSpacing(true);
        editorToolbar.getStyle()
            .set("border-bottom", "1px solid var(--lumo-contrast-10pct)")
            .set("background-color", "var(--lumo-contrast-5pct)");
    
        // Editor container
        VerticalLayout editorContainer = new VerticalLayout(editorToolbar, editor);
        editorContainer.setSizeFull();
        editorContainer.setPadding(false);
        editorContainer.setSpacing(false);
        editorContainer.setFlexGrow(1, editor);
    
        // Comments panel
        commentPanel.setPadding(true);
        commentPanel.setSpacing(true);
        commentPanel.getStyle()
            .set("border-top", "1px solid #ccc")
            .set("margin-top", "0.5rem")
            .set("background-color", "#fdfdfd");
    
        Div commentHeader = new Div("💬 Comments:");
        commentHeader.getStyle().set("font-weight", "bold");
        commentPanel.add(commentHeader);
    
        VerticalLayout fullEditorArea = new VerticalLayout(editorContainer, commentPanel);
        fullEditorArea.setSizeFull();
        fullEditorArea.setSpacing(false);
        fullEditorArea.setPadding(false);
        fullEditorArea.setFlexGrow(1, editorContainer);
    
        hiddenDownloadLink = new Anchor();
        hiddenDownloadLink.setId("hiddenDownloadLink");
        hiddenDownloadLink.getStyle().set("display", "none");
        hiddenDownloadLink.getElement().setAttribute("download", true);
    
        Section sidebar = SidebarUtil.createSidebar(viewCode, editCode, userId, hiddenDownloadLink, activeUserListSection);
    
        HorizontalLayout mainLayout = new HorizontalLayout(fullEditorArea, sidebar);
        mainLayout.setSizeFull();
        mainLayout.setFlexGrow(3, fullEditorArea);
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
        if (suppressInput) return;

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
        if (suppressInput || text.isEmpty()) return;
        
        saveStateToUndoStack(
            OperationType.BATCH_INSERT,
            text,
            position,
            editor.getValue()
        );
        
        for (int i = 0; i < text.length(); i++) {
            ClientEditRequest req = CollaborativeEditService.createInsertRequest(
                String.valueOf(text.charAt(i)), 
                position + i, 
                userId, 
                documentId
            );
            collaborativeEditService.sendEditRequest(req);
        }
    }

    @ClientCallable
    public void onCharacterBatchDeleted(int startPosition, int count) {
        if (suppressInput || count <= 0) return;

        String currentText = editor.getValue();
        if (startPosition >= 0 && startPosition + count <= currentText.length()) {
            String deletedText = currentText.substring(startPosition, startPosition + count);

            saveStateToUndoStack(
                OperationType.BATCH_DELETE,
                deletedText,
                startPosition,
                currentText
            );

            for (int i = 0; i < count; i++) {
                ClientEditRequest req = CollaborativeEditService.createDeleteRequest(
                    startPosition, userId, documentId
                );
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
        if (undoStack.isEmpty()) return;
        
        EditorState lastState = undoStack.pop();
        
        if (!userId.equals(lastState.userId())) {
            undo();
            return;
        }
        
        suppressInput = true;
        isUndoRedoOperation = true;
        try {
            String current = editor.getValue();
            switch (lastState.type()) {
                case INSERT -> {
                    int pos = lastState.position();
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
            suppressInput = false;
            isUndoRedoOperation = false;
        }
    }

    private void addCommentBox() {
        TextArea commentInput = new TextArea("Write your comment");
        commentInput.setWidthFull();
    
        Button postBtn = new Button("Post", e -> {
            String comment = commentInput.getValue();
            if (comment.isEmpty()) {
                Notification.show("Comment cannot be empty.");
                return;
            }
            String commentId = UUID.randomUUID().toString();
            sendAddComment(userId, commentId, comment, currentCursorPosition, currentCursorPosition + comment.length());
            // Show comment in panel
            renderComment(commentId, userId, comment, currentCursorPosition, currentCursorPosition + comment.length());
    
            // Clear input
            commentInput.clear();
        });
    
        commentPanel.add(commentInput, postBtn);
    }    
    
    private void sendAddComment(String userId, String commentId, String text, int start, int end) {
        ClientEditRequest req = CollaborativeEditService.createAddCommentRequest(documentId, userId, commentId, start, end, text);
        collaborativeEditService.sendEditRequest(req);
    }

    private void sendDeleteComment(String commentId) {
        ClientEditRequest req = CollaborativeEditService.createDeleteCommentRequest(documentId, userId, commentId);
        collaborativeEditService.sendEditRequest(req);
    }
    
    private void redo() {
        if (redoStack.isEmpty()) return;
        
        EditorState nextState = redoStack.pop();
        
        suppressInput = true;
        isUndoRedoOperation = true;
        try {
            String current = editor.getValue();
            switch (nextState.type()) {
                case INSERT -> {
                    int pos = nextState.position();
                    editor.setValue(
                        current.substring(0, pos) +
                        nextState.text() +
                        current.substring(pos)
                    );
                    currentCursorPosition = pos + nextState.text().length();
                }
                case DELETE -> {
                    int pos = nextState.position();
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
            suppressInput = false;
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
                updateActiveUserListUI(usernames);
            } catch (Exception e) {
                System.err.println("\u274C Failed to parse ACTIVE_USERS: " + e.getMessage());
            }
            return;
        }
    
        if (text.contains("\"type\":\"commentAdded\"")) {
            try {
                JsonObject json = JsonParser.parseString(text).getAsJsonObject();
                String commentId = json.get("commentId").getAsString();
                String userId = json.get("userId").getAsString();
                String commentText = json.get("text").getAsString();
                int start = json.get("startIndex").getAsInt();
                int end = json.get("endIndex").getAsInt();
                renderComment(commentId, userId, commentText, start, end);
            } catch (Exception e) {
                System.err.println("❌ Failed to parse commentAdded: " + e.getMessage());
            }
            return;
        }
    
        if (text.contains("\"type\":\"commentDeleted\"")) {
            try {
                JsonObject json = JsonParser.parseString(text).getAsJsonObject();
                String commentId = json.get("commentId").getAsString();
                removeComment(commentId);
            } catch (Exception e) {
                System.err.println("❌ Failed to parse commentDeleted: " + e.getMessage());
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

    private void renderComment(String commentId, String author, String text, int start, int end) {
        Div commentDiv = new Div("🗨 " + author + ": " + text);
        commentDiv.setId(commentId);
        commentDiv.getStyle()
            .set("padding", "5px")
            .set("margin", "4px 0")
            .set("background-color", "#eef");
    
        Button deleteBtn = new Button(" ", VaadinIcon.TRASH.create());
        deleteBtn.getStyle().set("margin-left", "10px").set("color", "red");
        deleteBtn.addClickListener(e -> sendDeleteComment(commentId));
    
        commentDiv.add(deleteBtn);
        commentPanel.add(commentDiv);
    }
    
    private void removeComment(String commentId) {
        commentPanel.getChildren()
            .filter(comp -> comp.getId().isPresent() && comp.getId().get().equals(commentId))
            .findFirst()
            .ifPresent(commentPanel::remove);
    }
    
}