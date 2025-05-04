package com.example.collaborative_editor.frontend.components;

import com.example.collaborative_editor.backend.crdt.CrdtChar;
import com.example.collaborative_editor.backend.crdt.CrdtDocument;
import com.example.collaborative_editor.backend.crdt.Identifier;
import com.example.collaborative_editor.backend.crdt.Operation;
import com.example.collaborative_editor.backend.websocket.WebSocketMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.KeyDownEvent;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.page.PendingJavaScriptResult;
import com.vaadin.flow.component.textfield.TextArea;
import elemental.json.Json;
import elemental.json.JsonArray;
import elemental.json.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@Tag("collaborative-editor")
@JsModule("./collaborative-editor.js")
public class CollaborativeEditor extends Div {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    private final String documentId;
    private final String userId;
    private final String sessionId;
    private final CrdtDocument localDocument;
    
    private TextArea textArea;
    private int ignoreNextChanges = 0;
    
    public CollaborativeEditor(String documentId, String userId, String sessionId) {
        this.documentId = documentId;
        this.userId = userId;
        this.sessionId = sessionId;
        this.localDocument = new CrdtDocument(userId);
        
        setupUI();
        setupWebSocket();
    }
    
    private void setupUI() {
        textArea = new TextArea();
        textArea.setSizeFull();
        textArea.setPlaceholder("Start typing...");
        
        // Handle text input
        textArea.addValueChangeListener(event -> {
            if (ignoreNextChanges > 0) {
                ignoreNextChanges--;
                return;
            }
            
            String oldText = event.getOldValue();
            String newText = event.getValue();
            
            // Calculate diff and apply operations
            calculateDiffAndApplyOperations(oldText, newText);
        });
        
        // Handle special keys
        textArea.addKeyDownListener(this::handleKeyDown);
        
        add(textArea);
    }
    
    private void handleKeyDown(KeyDownEvent event) {
        // Handle special keys (if needed)
        if (event.getKey() == Key.ENTER || event.getKey() == Key.TAB) {
            // Special handling for enter or tab 
        }
    }
    
    private void calculateDiffAndApplyOperations(String oldText, String newText) {
        // This is a simplified implementation
        // In a real app, you'd use a proper diff algorithm
        
        // Just for demonstration: handle simple append case
        if (oldText.length() < newText.length() && newText.startsWith(oldText)) {
            String addedText = newText.substring(oldText.length());
            int position = oldText.length();
            
            for (int i = 0; i < addedText.length(); i++) {
                handleLocalInsert(addedText.charAt(i) + "", position + i);
            }
        }
        // Handle simple delete case
        else if (oldText.length() > newText.length() && oldText.startsWith(newText)) {
            int deleteStart = newText.length();
            int deleteEnd = oldText.length();
            
            for (int i = deleteStart; i < deleteEnd; i++) {
                handleLocalDelete(deleteStart);
            }
        }
        // Otherwise, more complex change - would need proper diff algorithm
    }
    
    private void handleLocalInsert(String character, int position) {
        // Get characters before and after insertion point
        CrdtChar before = position > 0 ? localDocument.getCharacterAt(position - 1) : null;
        CrdtChar after = localDocument.getCharacterAt(position);
        
        // Generate position between
        List<Identifier> beforePos = before != null ? before.getPosition() : new ArrayList<>();
        List<Identifier> afterPos = after != null ? after.getPosition() : new ArrayList<>();
        List<Identifier> newPos = localDocument.generatePositionBetween(beforePos, afterPos);
        
        // Create character
        CrdtChar newChar = new CrdtChar(character, newPos, userId, System.currentTimeMillis());
        
        // Create operation
        Operation operation = new Operation(
                Operation.Type.INSERT,
                newChar,
                userId,
                sessionId,
                System.currentTimeMillis()
        );
        
        // Apply locally
        localDocument.applyOperation(operation);
        
        // Send to server
        sendOperation(operation);
    }
    
    private void handleLocalDelete(int position) {
        CrdtChar charToDelete = localDocument.getCharacterAt(position);
        if (charToDelete == null) return;
        
        // Create delete operation
        Operation operation = new Operation(
                Operation.Type.DELETE,
                charToDelete,
                userId,
                sessionId,
                System.currentTimeMillis()
        );
        
        // Apply locally
        localDocument.applyOperation(operation);
        
        // Send to server
        sendOperation(operation);
    }
    
    private void updateTextArea() {
        // Get text representation from CRDT
        String text = localDocument.toString();
        
        // Update TextArea without triggering change listener
        ignoreNextChanges++;
        textArea.setValue(text);
    }
    
    // src/main/java/com/editor/frontend/components/CollaborativeEditor.java (continued)
    private void setupWebSocket() {
        // JavaScript to setup WebSocket
        UI.getCurrent().getPage().executeJs(
                "window.editorWebSocket = new WebSocket(`ws://${window.location.host}/ws/editor`); " +
                "window.editorWebSocket.onmessage = function(event) { " +
                "   const message = JSON.parse(event.data); " +
                "   window.Vaadin.Flow.clients.ROOT.collaborativeEditorMessageCallback(message); " +
                "}; " +
                "window.editorWebSocket.onopen = function() { " +
                "   const joinMsg = {" +
                "       type: 'JOIN', " +
                "       userId: $0, " +
                "       sessionId: $1, " +
                "       documentId: $2, " +
                "       timestamp: Date.now() " +
                "   }; " +
                "   window.editorWebSocket.send(JSON.stringify(joinMsg)); " +
                "}; " +
                "true;",
                userId, sessionId, documentId);
        
        // Register callback for WebSocket messages
        UI.getCurrent().getPage().addJavaScript("window.Vaadin.Flow.collaborativeEditorMessageCallback = function(message) { " +
                "$0.$server.handleWebSocketMessage(JSON.stringify(message)); " +
                "};", getElement());
    }
    
    public void connect() {
        // Already set up in setupWebSocket
    }
    
    public void disconnect() {
        UI.getCurrent().getPage().executeJs("if(window.editorWebSocket) window.editorWebSocket.close();");
    }
    
    private void sendOperation(Operation operation) {
        // Convert operation to WebSocketMessage format
        WebSocketMessage wsMessage = new WebSocketMessage();
        wsMessage.setType(operation.getType().toString());
        wsMessage.setUserId(operation.getUserId());
        wsMessage.setSessionId(operation.getSessionId());
        wsMessage.setTimestamp(operation.getTimestamp());
        wsMessage.setDocumentId(documentId);
        
        // Convert character and position
        WebSocketMessage.CharacterDTO charDTO = new WebSocketMessage.CharacterDTO();
        charDTO.setValue(operation.getCharacter().getValue());
        charDTO.setDeleted(operation.getCharacter().isDeleted());
        
        // Convert position
        List<Identifier> position = operation.getCharacter().getPosition();
        WebSocketMessage.PositionDTO[] positionDTOs = new WebSocketMessage.PositionDTO[position.size()];
        
        for (int i = 0; i < position.size(); i++) {
            WebSocketMessage.PositionDTO posDTO = new WebSocketMessage.PositionDTO();
            posDTO.setDigit(position.get(i).getDigit());
            posDTO.setSiteId(position.get(i).getSiteId());
            positionDTOs[i] = posDTO;
        }
        
        charDTO.setPosition(positionDTOs);
        wsMessage.setCharacter(charDTO);
        
        // Send through WebSocket
        try {
            String json = objectMapper.writeValueAsString(wsMessage);
            UI.getCurrent().getPage().executeJs("window.editorWebSocket.send($0);", json);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    // Server RPC called from JavaScript
    public void handleWebSocketMessage(String messageJson) {
        try {
            WebSocketMessage message = objectMapper.readValue(messageJson, WebSocketMessage.class);
            
            // Handle different message types
            switch (message.getType()) {
                case "DOCUMENT_STATE":
                    // Handle initial document state
                    // In a real app, this would populate the document from a saved state
                    break;
                    
                case "INSERT":
                case "DELETE":
                    // Convert message to operation
                    Operation operation = convertToOperation(message);
                    
                    // Apply to local document
                    localDocument.applyOperation(operation);
                    
                    // Update UI
                    updateTextArea();
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private Operation convertToOperation(WebSocketMessage message) {
        WebSocketMessage.CharacterDTO charDTO = message.getCharacter();
        
        // Convert position DTOs to Identifiers
        List<Identifier> position = new ArrayList<>();
        for (WebSocketMessage.PositionDTO posDTO : charDTO.getPosition()) {
            position.add(new Identifier(posDTO.getDigit(), posDTO.getSiteId()));
        }
        
        // Create CRDT character
        CrdtChar crdtChar = new CrdtChar(
                charDTO.getValue(), 
                position,
                message.getUserId(),
                message.getTimestamp()
        );
        
        if (charDTO.isDeleted()) {
            crdtChar.setDeleted(true);
        }
        
        // Create operation
        Operation.Type type = Operation.Type.valueOf(message.getType());
        return new Operation(
                type,
                crdtChar,
                message.getUserId(),
                message.getSessionId(),
                message.getTimestamp()
        );
    }
}