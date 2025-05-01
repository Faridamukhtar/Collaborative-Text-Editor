package com.example.application.views.pageeditor;

import com.example.application.views.pageeditor.CRDT.*;
import com.example.application.views.pageeditor.socketsLogic.*;

import com.vaadin.flow.component.*;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.richtexteditor.*;
import com.vaadin.flow.router.*;
import com.vaadin.flow.shared.Registration;
import com.vaadin.flow.theme.lumo.LumoUtility.*;
import org.vaadin.lineawesome.LineAwesomeIconUrl;

import java.util.*;
import java.util.Timer;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.TimerTask;

@PageTitle("Collaborative Editor")
@Route("collab-editor")
public class CollaborativeEditor extends Div {
    private final RichTextEditor editor;
    private final ClientRGA documentModel;
    private final CollaborationService collaborationService;
    private String lastContent = "";
    private SelectionState currentSelection = new SelectionState(0, 0);
    private Registration editorListener;
    private Timer debounceTimer;

    public CollaborativeEditor(CollaborationService collaborationService) {
        this.collaborationService = collaborationService;
        this.documentModel = new ClientRGA();
        
        // Setup UI
        addClassNames(Display.FLEX, Flex.GROW, Height.FULL);
        editor = new RichTextEditor();
        editor.addClassNames(Flex.GROW, Height.FULL);
        add(editor);
        
        // Initialize collaboration
        initializeCollaboration();
    }

    private void initializeCollaboration() {
        // 1. Setup editor listeners
        setupEditorListeners();
        
        // 2. Connect to collaboration service
        collaborationService.connect();
        
        // 3. Load initial document state
        collaborationService.requestInitialState().thenAccept(this::initializeDocument);
        
        // 4. Subscribe to remote changes
        collaborationService.subscribeToChanges(this::handleRemoteOperation);
    }

    private void setupEditorListeners() {
        // Track selection changes
        editor.getElement().addEventListener("cursor-changed", e -> {
            currentSelection = new SelectionState(
                ((Double) e.getEventData().getNumber("editor.__selection.range.startOffset")).intValue(),
                ((Double) e.getEventData().getNumber("editor.__selection.range.endOffset")).intValue()
            );
        }).addEventData("editor.__selection.range.startOffset")
          .addEventData("editor.__selection.range.endOffset");

        // Listen for content changes with debouncing
        editorListener = editor.addValueChangeListener(e -> {
            if (e.isFromClient()) {
                debounceContentChange(e.getValue());
            }
        });
    }

    private void debounceContentChange(String newContent) {
        if (debounceTimer != null) {
            debounceTimer.cancel();
        }
        
        debounceTimer = new Timer();
        debounceTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                getUI().ifPresent(ui -> ui.access(() -> {
                    handleLocalChange(newContent);
                }));
            }
        }, 300); // 300ms debounce
    }

    private void initializeDocument(String initialContent) {
        getUI().ifPresent(ui -> ui.access(() -> {
            // Initialize with empty content first
            documentModel.initialize("", documentModel.getClientId());
            
            // Apply each character as an insert operation
            for (int i = 0; i < initialContent.length(); i++) {
                TextOperation op = TextOperation.createInsert(
                    initialContent.charAt(i),
                    String.valueOf(i),
                    System.currentTimeMillis(),
                    documentModel.getClientId(),
                    i > 0 ? String.valueOf(i-1) : null,
                    i < initialContent.length()-1 ? String.valueOf(i+1) : null
                );
                documentModel.applyLocal(op);
            }
            
            editor.setValue(initialContent);
            lastContent = initialContent;
        }));
    }

    private void handleLocalChange(String newContent) {
        List<TextOperation> operations = calculateOperations(lastContent, newContent);
        
        operations.forEach(op -> {
            documentModel.applyLocal(op);
            collaborationService.sendOperation(op);
        });
        
        lastContent = newContent;
    }

    private List<TextOperation> calculateOperations(String oldText, String newText) {
        List<TextOperation> operations = new ArrayList<>();
        
        // Handle content reset
        if (oldText.isEmpty() && !newText.isEmpty()) {
            for (int i = 0; i < newText.length(); i++) {
                operations.add(createInsertOperation(newText.charAt(i), i));
            }
            return operations;
        }

        // Find difference
        int commonPrefix = 0;
        int minLength = Math.min(oldText.length(), newText.length());
        
        while (commonPrefix < minLength && 
               oldText.charAt(commonPrefix) == newText.charAt(commonPrefix)) {
            commonPrefix++;
        }
        
        int commonSuffix = 0;
        while (commonSuffix < minLength - commonPrefix &&
               oldText.charAt(oldText.length() - 1 - commonSuffix) == 
               newText.charAt(newText.length() - 1 - commonSuffix)) {
            commonSuffix++;
        }
        
        // Handle deletions (right to left)
        for (int i = oldText.length() - commonSuffix - 1; i >= commonPrefix; i--) {
            operations.add(createDeleteOperation(i));
        }
        
        // Handle insertions
        String inserted = newText.substring(commonPrefix, newText.length() - commonSuffix);
        for (int i = 0; i < inserted.length(); i++) {
            operations.add(createInsertOperation(
                inserted.charAt(i), 
                commonPrefix + i
            ));
        }
        
        return operations;
    }

    private TextOperation createInsertOperation(char c, int pos) {
        return TextOperation.createInsert(
            c,
            String.valueOf(pos),
            System.currentTimeMillis(),
            documentModel.getClientId(),
            pos > 0 ? String.valueOf(pos-1) : null,
            null
        );
    }

    private TextOperation createDeleteOperation(int pos) {
        return TextOperation.createDelete(
            String.valueOf(pos),
            System.currentTimeMillis(),
            documentModel.getClientId()
        );
    }

    private void handleRemoteOperation(TextOperation operation) {
        getUI().ifPresent(ui -> ui.access(() -> {
            // Save current selection
            SelectionState selectionBefore = currentSelection;
            
            // Apply remote operation
            documentModel.applyRemote(operation);
            
            // Get current content from model
            String currentContent = documentModel.getContent();
            String editorContent = editor.getValue().replaceAll("\\<[^>]*>", "");
            
            // Only update if different
            if (!currentContent.equals(editorContent)) {
                editor.setValue(currentContent);
                
                // Transform and restore selection
                SelectionState transformed = transformSelection(selectionBefore, operation);
                setSelection(transformed);
            }
            
            lastContent = currentContent;
        }));
    }

    private SelectionState transformSelection(SelectionState selection, TextOperation op) {
        try {
            int opPosition = Integer.parseInt(op.getPositionId());
            
            switch (op.getType()) {
                case INSERT:
                    if (opPosition <= selection.getStart()) {
                        return new SelectionState(
                            selection.getStart() + 1,
                            selection.getEnd() + 1
                        );
                    }
                    break;
                    
                case DELETE:
                    if (opPosition < selection.getStart()) {
                        return new SelectionState(
                            selection.getStart() - 1,
                            selection.getEnd() - 1
                        );
                    } else if (opPosition == selection.getStart() && 
                               selection.getStart() == selection.getEnd()) {
                        return new SelectionState(
                            Math.max(0, selection.getStart() - 1),
                            Math.max(0, selection.getEnd() - 1)
                        );
                    }
                    break;
            }
        } catch (NumberFormatException e) {
            // Position ID wasn't a simple integer
        }
        return selection;
    }

    private void setSelection(SelectionState selection) {
        getElement().executeJs(
            "this.$server.setEditorSelection($0, $1)", 
            selection.getStart(),
            selection.getEnd()
        );
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        if (editorListener != null) editorListener.remove();
        if (debounceTimer != null) debounceTimer.cancel();
        collaborationService.disconnect();
        super.onDetach(detachEvent);
    }
}