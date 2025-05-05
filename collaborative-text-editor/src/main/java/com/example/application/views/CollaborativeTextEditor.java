package com.example.application.views;

import com.example.application.connections.CRDT.*;
import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.function.SerializableConsumer;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.Route;
import org.springframework.beans.factory.annotation.Autowired;

import com.vaadin.flow.server.StreamResource;
import com.vaadin.flow.server.VaadinSession;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import com.example.application.views.components.SidebarUtil;
import com.example.application.views.components.helpers;

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
    private static final ConcurrentHashMap<String, UI> activeUsers = new ConcurrentHashMap<>();

    // Undo/Redo functionality
    private final Deque<EditorState> undoStack = new ArrayDeque<>(5);
    private final Deque<EditorState> redoStack = new ArrayDeque<>(5);
    private boolean isUndoRedoOperation = false;
    private int currentCursorPosition = 0;

    @Autowired
    private CollaborativeEditService collaborativeEditService;

    // Record to store editor state
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
        initializeEditorUi();
    }

    private void initializeEditorUi() {
        collaborativeEditService.connectWebSocket(documentId);
        String content = (String) VaadinSession.getCurrent().getAttribute("importedText");

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
        title.addClassName("header");
    
        // Create a Div for the header and set padding
        Div header = new Div(title);
        header.getStyle().set("text-align", "left");
    
        // Editor
        editor = new TextArea();
        if(content != null) {
            onCharacterBatchInserted(content,0);
            editor.setValue(content);
            saveStateToUndoStack(content, 0); // Save initial state
        }
        if ("viewer".equals(role))
            editor.setReadOnly(true);
        editor.setWidthFull();
        editor.setHeight("100%");
        editor.setLabel("Edit text below - changes are shared with all users");

        if (!viewCode.isEmpty() && editCode.isEmpty()) {
            editor.setReadOnly(true); 
        }  
    
        // Modified value change listener
        editor.addValueChangeListener(event -> {
            if (!suppressInput && !isUndoRedoOperation) {
                // Only save state if content actually changed
                if (undoStack.isEmpty() || !undoStack.peek().content().equals(event.getValue())) {
                    saveStateToUndoStack(event.getValue(), currentCursorPosition);
                    redoStack.clear();
                }
                updateExportResource(event.getValue());
            }
        });

        // Track cursor position
        editor.getElement().addEventListener("keyup", e -> updateCursorPosition());
        editor.getElement().addEventListener("click", e -> updateCursorPosition());
    
        // Create editor toolbar
        HorizontalLayout editorToolbar = new HorizontalLayout();
        editorToolbar.setWidthFull();
        editorToolbar.setPadding(true);
        editorToolbar.setSpacing(true);
        editorToolbar.getStyle()
            .set("border-bottom", "1px solid var(--lumo-contrast-10pct)")
            .set("background-color", "var(--lumo-contrast-5pct)");

        // Undo/Redo buttons
        Button undoButton = new Button("Undo", VaadinIcon.ARROW_BACKWARD.create());
        undoButton.addClickListener(e -> undo());
        undoButton.setEnabled(!undoStack.isEmpty());

        Button redoButton = new Button("Redo", VaadinIcon.ARROW_FORWARD.create());
        redoButton.addClickListener(e -> redo());
        redoButton.setEnabled(!redoStack.isEmpty());

        // Export button
        Button exportButton = new Button("Export", VaadinIcon.DOWNLOAD.create());
        exportButton.addClickListener(e -> 
            UI.getCurrent().getPage().executeJs("document.getElementById('hiddenDownloadLink').click();")
        );

        // Add buttons to toolbar with spacer
        editorToolbar.add(undoButton, redoButton);
        editorToolbar.addAndExpand(new Div()); // Spacer pushes export to right
        editorToolbar.add(exportButton);

        // Create editor container
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

        CollaborativeEditService.setUiListener(this);
        Div connectionStatus = new Div();
        connectionStatus.setText("Connected Users: " + activeUsers.size());
        connectionStatus.getStyle().set("color", "black");
    
        VerticalLayout sidebarLayout = new VerticalLayout();
        sidebarLayout.setWidth("300px");
        sidebarLayout.getStyle()
            .set("background-color", "#f5f5f5")
            .set("padding", "1rem");
        sidebarLayout.addClassNames("sidebar");
        sidebarLayout.add(
            SidebarUtil.createItem("Owner", userId),
            SidebarUtil.createBadgeItem("Document ID", documentId),
            SidebarUtil.createBadgeItem("View Code", viewCode),
            SidebarUtil.createBadgeItem("Edit Code", editCode),
            hiddenDownloadLink,
            connectionStatus
        );
    
        HorizontalLayout mainLayout = new HorizontalLayout(editorContainer, sidebarLayout);
        mainLayout.setSizeFull();
        mainLayout.setFlexGrow(3, editorContainer);
        mainLayout.setFlexGrow(1, sidebarLayout);
    
        add(mainLayout);
    
        initializeConnector();
    }

    private void updateCursorPosition() {
        editor.getElement().executeJs("return this.inputElement.selectionStart")
            .then(Integer.class, (SerializableConsumer<Integer>) pos -> {
                currentCursorPosition = pos;
            });
    }
    
    private void initializeConnector() {
        getElement().executeJs("window.initEditorConnector($0, $1)", getElement(), userId);
    }

    private void saveStateToUndoStack(String content, int cursorPosition) {
        if (undoStack.size() >= 5) {
            undoStack.removeFirst();
        }
        undoStack.push(new EditorState(content, cursorPosition));
    }

    private void saveStateToRedoStack(String content, int cursorPosition) {
        if (redoStack.size() >= 5) {
            redoStack.removeFirst();
        }
        redoStack.push(new EditorState(content, cursorPosition));
    }

    private void undo() {
        if (undoStack.size() > 1) {
            // Save current state to redo stack
            saveStateToRedoStack(editor.getValue(), currentCursorPosition);
            
            // Remove current state from undo stack
            undoStack.pop();
            
            // Get and apply previous state
            EditorState previousState = undoStack.peek();
            applyState(previousState);
        }
    }
    
    private void redo() {
        if (!redoStack.isEmpty()) {
            // Save current state back to undo stack
            saveStateToUndoStack(editor.getValue(), currentCursorPosition);
            
            // Get and apply next state from redo stack
            EditorState nextState = redoStack.pop();
            applyState(nextState);
        }
    }
    
    private void applyState(EditorState state) {
        isUndoRedoOperation = true;
        suppressInput = true;
        
        ui.access(() -> {
            try {
                String currentValue = editor.getValue();
                if (!currentValue.equals(state.content())) {
                    editor.setValue(state.content());
                }
                
                // Set cursor position asynchronously after value is set
                ui.getPage().executeJs(
                    "setTimeout(() => { " +
                    "const el = $0.inputElement; " +
                    "el.selectionStart = $1; " +
                    "el.selectionEnd = $1; " +
                    "}, 10)",
                    editor.getElement(),
                    state.cursorPosition()
                );
                
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
            String ch = String.valueOf(text.charAt(i));
            onCharacterInserted(ch, position + i);
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
        System.out.println("userId: " + userId);
        System.out.println("📩 Message received from server in UI: " + text);
        suppressInput = true;
        ui.access(() -> {
            editor.setValue(text);
            saveStateToUndoStack(text, currentCursorPosition);
            suppressInput = false;
        });
    }

    private void updateExportResource(String htmlContent) {
        String plainText = helpers.htmlToPlainText(htmlContent);
        StreamResource resource = new StreamResource("document.txt",
                () -> new ByteArrayInputStream(plainText.getBytes(StandardCharsets.UTF_8)));
        hiddenDownloadLink.setHref(resource);
    }
}