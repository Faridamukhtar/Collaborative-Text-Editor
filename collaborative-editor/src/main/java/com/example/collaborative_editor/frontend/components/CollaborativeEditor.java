package com.example.collaborative_editor.frontend.components;

import com.example.collaborative_editor.backend.crdt.CrdtChar;
import com.example.collaborative_editor.backend.crdt.CrdtDocument;
import com.example.collaborative_editor.backend.crdt.Identifier;
import com.example.collaborative_editor.backend.crdt.Operation;
import com.example.collaborative_editor.backend.websocket.WebSocketMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.KeyDownEvent;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.page.PendingJavaScriptResult;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.server.Command;
import com.vaadin.flow.shared.Registration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A collaborative text editor component that uses CRDTs for conflict-free
 * real-time collaboration. All WebSocket communication is handled in Java.
 */
@Tag("collaborative-editor")
@JsModule("./collaborative-editor.js")
public class CollaborativeEditor extends Div {
    private static final Logger logger = LoggerFactory.getLogger(CollaborativeEditor.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final ExecutorService executorService = Executors.newCachedThreadPool();
    
    private final String documentId;
    private final String userId;
    private final String sessionId;
    private final CrdtDocument localDocument;
    private final ReentrantLock documentLock = new ReentrantLock();
    
    private TextArea textArea;
    private int ignoreNextChanges = 0;
    private boolean isConnected = false;
    private int cursorPosition = 0;
    private WebSocketSession webSocketSession;
    private Registration uiRefresher;

    private enum DiffOperation {
        INSERT, DELETE, REPLACE
    }
    
    private static class TextDiff {
        DiffOperation operation;
        int position;
        String text;
        
        TextDiff(DiffOperation operation, int position, String text) {
            this.operation = operation;
            this.position = position;
            this.text = text;
        }
    }
    
    /**
     * Creates a new collaborative editor instance.
     *
     * @param documentId Unique identifier for the document being edited
     * @param userId Unique identifier for the current user
     * @param sessionId Unique identifier for this editing session
     */
    public CollaborativeEditor(String documentId, String userId, String sessionId) {
        this.documentId = documentId;
        this.userId = userId;
        this.sessionId = sessionId;
        this.localDocument = new CrdtDocument(userId);
        
        setupUI();
    }
    
    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        
        // Start a UI refresher to handle WebSocket messages in the UI thread
        uiRefresher = attachEvent.getUI().addPollListener(event -> {
            // This will be called periodically to refresh the UI
        });
        
        // Connect to WebSocket
        connect();
    }
    
    @Override
    protected void onDetach(DetachEvent detachEvent) {
        // Disconnect WebSocket
        disconnect();
        
        // Remove UI refresher
        if (uiRefresher != null) {
            uiRefresher.remove();
            uiRefresher = null;
        }
        
        super.onDetach(detachEvent);
    }
    
    private void setupUI() {
        textArea = new TextArea();
        textArea.setSizeFull();
        textArea.setPlaceholder("Start typing...");
        
        // Save cursor position on focus/click
        textArea.getElement().executeJs(
            "this.addEventListener('click', function() {" +
            "   const cursorPos = this.selectionStart;" +
            "   $0.$server.updateCursorPosition(cursorPos);" +
            "});" +
            "this.addEventListener('keyup', function() {" +
            "   const cursorPos = this.selectionStart;" +
            "   $0.$server.updateCursorPosition(cursorPos);" +
            "});", getElement());
        
        // Handle text input
        textArea.addValueChangeListener(event -> {
            if (ignoreNextChanges > 0) {
                ignoreNextChanges--;
                return;
            }
            
            String oldText = event.getOldValue();
            String newText = event.getValue();
            
            List<TextDiff> diffs = calculateDiff(oldText, newText);
            applyLocalDiffs(diffs);
        });
        
        // Handle special keys
        textArea.addKeyDownListener(this::handleKeyDown);
        
        add(textArea);
    }
    
    /**
     * Updates the stored cursor position.
     *
     * @param position Current cursor position in the text area
     */
    public void updateCursorPosition(int position) {
        this.cursorPosition = position;
    }
    
    private void handleKeyDown(KeyDownEvent event) {
        // Handle special keys like Tab without losing focus
        if (event.getKey() == Key.TAB) {
            int pos = cursorPosition;
            
            // Insert tab character
            String text = textArea.getValue();
            String newText = text.substring(0, pos) + "\t" + text.substring(pos);
            
            ignoreNextChanges++;
            textArea.setValue(newText);
            
            // Update cursor position
            UI.getCurrent().getPage().executeJs(
                "setTimeout(function() {" +
                "  const element = $0;" +
                "  element.focus();" +
                "  element.setSelectionRange($1, $1);" +
                "}, 10);", textArea.getElement(), pos + 1);
            
            // Handle tab insertion as an operation
            handleLocalInsert("\t", pos);
        }
    }
    
    /**
     * Calculates the differences between old text and new text.
     * This is a more robust implementation using longest common subsequence approach.
     *
     * @param oldText Previous text value
     * @param newText Current text value
     * @return List of text differences
     */
    private List<TextDiff> calculateDiff(String oldText, String newText) {
        List<TextDiff> diffs = new ArrayList<>();
        
        // Simple cases first
        if (oldText.equals(newText)) {
            return diffs;
        }
        
        // Case 1: Simple append
        if (newText.startsWith(oldText)) {
            String addedText = newText.substring(oldText.length());
            diffs.add(new TextDiff(DiffOperation.INSERT, oldText.length(), addedText));
            return diffs;
        }
        
        // Case 2: Simple delete at end
        if (oldText.startsWith(newText)) {
            diffs.add(new TextDiff(DiffOperation.DELETE, newText.length(), 
                    oldText.substring(newText.length())));
            return diffs;
        }
        
        // For more complex edits, find common prefix and suffix
        int prefixLength = findCommonPrefixLength(oldText, newText);
        String oldTextWithoutPrefix = oldText.substring(prefixLength);
        String newTextWithoutPrefix = newText.substring(prefixLength);
        
        int suffixLength = findCommonSuffixLength(oldTextWithoutPrefix, newTextWithoutPrefix);
        
        // Calculate the central differing portions
        String oldDiff = oldTextWithoutPrefix.substring(0, oldTextWithoutPrefix.length() - suffixLength);
        String newDiff = newTextWithoutPrefix.substring(0, newTextWithoutPrefix.length() - suffixLength);
        
        // If old portion is empty, it's an insert
        if (oldDiff.isEmpty()) {
            diffs.add(new TextDiff(DiffOperation.INSERT, prefixLength, newDiff));
        } 
        // If new portion is empty, it's a delete
        else if (newDiff.isEmpty()) {
            diffs.add(new TextDiff(DiffOperation.DELETE, prefixLength, oldDiff));
        } 
        // Otherwise it's a replace (delete + insert)
        else {
            diffs.add(new TextDiff(DiffOperation.DELETE, prefixLength, oldDiff));
            diffs.add(new TextDiff(DiffOperation.INSERT, prefixLength, newDiff));
        }
        
        return diffs;
    }
    
    private int findCommonPrefixLength(String s1, String s2) {
        int maxLength = Math.min(s1.length(), s2.length());
        for (int i = 0; i < maxLength; i++) {
            if (s1.charAt(i) != s2.charAt(i)) {
                return i;
            }
        }
        return maxLength;
    }
    
    private int findCommonSuffixLength(String s1, String s2) {
        int maxLength = Math.min(s1.length(), s2.length());
        for (int i = 1; i <= maxLength; i++) {
            if (s1.charAt(s1.length() - i) != s2.charAt(s2.length() - i)) {
                return i - 1;
            }
        }
        return maxLength;
    }
    
    private void applyLocalDiffs(List<TextDiff> diffs) {
        try {
            documentLock.lock();
            for (TextDiff diff : diffs) {
                switch (diff.operation) {
                    case INSERT:
                        for (int i = 0; i < diff.text.length(); i++) {
                            handleLocalInsert(diff.text.charAt(i) + "", diff.position + i);
                        }
                        break;
                    case DELETE:
                        for (int i = 0; i < diff.text.length(); i++) {
                            handleLocalDelete(diff.position);
                        }
                        break;
                    case REPLACE:
                        // Handled as a combination of DELETE and INSERT in the diff calculation
                        break;
                }
            }
        } finally {
            documentLock.unlock();
        }
    }
    
    private void handleLocalInsert(String character, int position) {
        try {
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
            if (isConnected) {
                sendOperation(operation);
            }
        } catch (Exception e) {
            logger.error("Error handling local insert: ", e);
            showNotification("Error applying edit: " + e.getMessage());
        }
    }
    
    private void handleLocalDelete(int position) {
        try {
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
            if (isConnected) {
                sendOperation(operation);
            }
        } catch (Exception e) {
            logger.error("Error handling local delete: ", e);
            showNotification("Error applying delete: " + e.getMessage());
        }
    }
    
    /**
     * Updates the UI with the current document content.
     * Preserves cursor position where possible.
     */
    private void updateTextArea() {
        try {
            documentLock.lock();
            // Get text representation from CRDT
            String text = localDocument.toString();
            
            // Store cursor position
            int cursorPos = Math.min(cursorPosition, text.length());
            
            // Update TextArea without triggering change listener
            ignoreNextChanges++;
            textArea.setValue(text);
            
            // Restore cursor position
            UI.getCurrent().getPage().executeJs(
                "setTimeout(function() {" +
                "  const element = $0;" +
                "  element.focus();" +
                "  element.setSelectionRange($1, $1);" +
                "}, 10);", textArea.getElement(), cursorPos);
        } finally {
            documentLock.unlock();
        }
    }
    
    /**
     * Shows a notification in the UI thread.
     */
    private void showNotification(String message) {
        UI ui = getUI().orElse(null);
        if (ui != null) {
            ui.access(() -> {
                Notification.show(message, 3000, Notification.Position.TOP_END);
            });
        }
    }
    
    /**
     * Runs a command in the UI thread.
     */
    private void runInUI(Command command) {
        UI ui = getUI().orElse(null);
        if (ui != null) {
            ui.access(command);
        }
    }
    
    /**
     * Connects to the WebSocket server.
     */
    public void connect() {
        if (isConnected) return;
        
        try {
            // Create WebSocket client
            StandardWebSocketClient client = new StandardWebSocketClient();
            
            // Get server URL
            PendingJavaScriptResult result = UI.getCurrent().getPage().executeJs(
                "return window.location.protocol === 'https:' ? 'wss://' : 'ws://' + window.location.host + '/ws/editor'");
            
            String wsUrl = result.toCompletableFuture().get().asString();
            
            // Connect to WebSocket server
            client.doHandshake(new EditorWebSocketHandler(), null, new URI(wsUrl))
                .completable()
                .thenAccept(session -> {
                    webSocketSession = session;
                    isConnected = true;
                    
                    // Send JOIN message
                    WebSocketMessage joinMessage = new WebSocketMessage();
                    joinMessage.setType("JOIN");
                    joinMessage.setUserId(userId);
                    joinMessage.setSessionId(sessionId);
                    joinMessage.setDocumentId(documentId);
                    joinMessage.setTimestamp(System.currentTimeMillis());
                    
                    sendMessage(joinMessage);
                    
                    // Request full document state
                    requestFullDocumentState();
                    
                    runInUI(() -> {
                        showNotification("Connected to document: " + documentId);
                    });
                })
                .exceptionally(e -> {
                    logger.error("Error connecting to WebSocket: ", e);
                    isConnected = false;
                    runInUI(() -> {
                        showNotification("Connection error: " + e.getMessage());
                        // Try to reconnect after delay
                        scheduleReconnect();
                    });
                    return null;
                });
        } catch (Exception e) {
            logger.error("Error setting up WebSocket connection: ", e);
            showNotification("Connection setup error: " + e.getMessage());
            scheduleReconnect();
        }
    }
    
    /**
     * Schedules a reconnection attempt after a delay.
     */
    private void scheduleReconnect() {
        executorService.submit(() -> {
            try {
                Thread.sleep(5000);
                runInUI(this::connect);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }
    
    /**
     * Disconnects from the WebSocket server.
     */
    public void disconnect() {
        if (webSocketSession != null && webSocketSession.isOpen()) {
            try {
                // Send LEAVE message
                WebSocketMessage leaveMessage = new WebSocketMessage();
                leaveMessage.setType("LEAVE");
                leaveMessage.setUserId(userId);
                leaveMessage.setSessionId(sessionId);
                leaveMessage.setDocumentId(documentId);
                leaveMessage.setTimestamp(System.currentTimeMillis());
                
                sendMessage(leaveMessage);
                
                // Close connection
                webSocketSession.close();
            } catch (Exception e) {
                logger.error("Error disconnecting WebSocket: ", e);
            }
        }
        isConnected = false;
        webSocketSession = null;
    }
    
    /**
     * Requests the server to provide the current state of the document.
     */
    public void requestFullDocumentState() {
        if (!isConnected) return;
        
        try {
            WebSocketMessage requestMessage = new WebSocketMessage();
            requestMessage.setType("REQUEST_DOCUMENT");
            requestMessage.setUserId(userId);
            requestMessage.setSessionId(sessionId);
            requestMessage.setDocumentId(documentId);
            requestMessage.setTimestamp(System.currentTimeMillis());
            
            sendMessage(requestMessage);
        } catch (Exception e) {
            logger.error("Error requesting document state: ", e);
        }
    }
    
    /**
     * Sends an operation to the server.
     */
    private void sendOperation(Operation operation) {
        if (!isConnected) return;
        
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
        sendMessage(wsMessage);
    }
    
    /**
     * Sends a WebSocketMessage to the server.
     */
    private void sendMessage(WebSocketMessage message) {
        if (webSocketSession == null || !webSocketSession.isOpen()) {
            logger.warn("Cannot send message, WebSocket not connected");
            return;
        }
        
        try {
            String json = objectMapper.writeValueAsString(message);
            webSocketSession.sendMessage(new TextMessage(json));
        } catch (Exception e) {
            logger.error("Error sending WebSocket message: ", e);
            handleConnectionFailure(e);
        }
    }
    
    /**
     * Handles WebSocket connection failures.
     */
    private void handleConnectionFailure(Exception e) {
        if (isConnected) {
            isConnected = false;
            showNotification("Connection lost: " + e.getMessage() + ". Reconnecting...");
            scheduleReconnect();
        }
    }
    
    /**
     * Handles incoming WebSocket messages from the server.
     */
    private void handleWebSocketMessage(String messageJson) {
        try {
            WebSocketMessage message = objectMapper.readValue(messageJson, WebSocketMessage.class);
            
            // Handle different message types
            switch (message.getType()) {
                case "DOCUMENT_STATE":
                    handleDocumentState(message);
                    break;
                    
                case "INSERT":
                case "DELETE":
                    // Convert message to operation
                    Operation operation = convertToOperation(message);
                    
                    // Apply to local document
                    try {
                        documentLock.lock();
                        localDocument.applyOperation(operation);
                    } finally {
                        documentLock.unlock();
                    }
                    
                    // Update UI
                    runInUI(this::updateTextArea);
                    break;
                    
                case "USER_JOINED":
                    showNotification("User " + message.getUserId() + " joined the document");
                    break;
                    
                case "USER_LEFT":
                    showNotification("User " + message.getUserId() + " left the document");
                    break;
                    
                case "ERROR":
                    showNotification("Error");
                    break;
            }
        } catch (Exception e) {
            logger.error("Error processing WebSocket message: ", e);
        }
    }
    
    /**
     * Handles document state message - typically sent when a client first joins
     * or reconnects and needs the full document state.
     */
    private void handleDocumentState(WebSocketMessage message) {
        try {
            // Clear current document
            documentLock.lock();
            localDocument.clear();
            
            // Apply all characters from the message
            WebSocketMessage.CharacterDTO character = message.getCharacter();
            if (character != null) {
                CrdtChar crdtChar = convertToCrdtChar(character, message.getUserId());
                
                Operation op = new Operation(
                        Operation.Type.INSERT,
                        crdtChar,
                        message.getUserId(),
                        message.getSessionId(),
                        message.getTimestamp()
                );
                
                localDocument.applyOperation(op);
            }
        } finally {
            documentLock.unlock();
        }
        
        // Update UI
        runInUI(() -> {
            updateTextArea();
            showNotification("Document loaded successfully");
        });
    }
    
    private CrdtChar convertToCrdtChar(WebSocketMessage.CharacterDTO charDTO, String userId) {
        // Convert position DTOs to Identifiers
        List<Identifier> position = new ArrayList<>();
        for (WebSocketMessage.PositionDTO posDTO : charDTO.getPosition()) {
            position.add(new Identifier(posDTO.getDigit(), posDTO.getSiteId()));
        }
        
        // Create CRDT character
        CrdtChar crdtChar = new CrdtChar(
                charDTO.getValue(), 
                position,
                userId,
                System.currentTimeMillis()
        );
        
        if (charDTO.isDeleted()) {
            crdtChar.setDeleted(true);
        }
        
        return crdtChar;
    }
    
    private Operation convertToOperation(WebSocketMessage message) {
        CrdtChar crdtChar = convertToCrdtChar(message.getCharacter(), message.getUserId());
        
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
    
    /**
     * WebSocket handler for editor messages.
     */
    private class EditorWebSocketHandler extends TextWebSocketHandler {
        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            handleWebSocketMessage(message.getPayload());
        }
        
        @Override
        public void afterConnectionClosed(WebSocketSession session, org.springframework.web.socket.CloseStatus status) {
            if (session == webSocketSession) {
                isConnected = false;
                webSocketSession = null;
                showNotification("Connection closed: " + status.getReason() + ". Reconnecting...");
                scheduleReconnect();
            }
        }
        
        @Override
        public void handleTransportError(WebSocketSession session, Throwable exception) {
            if (session == webSocketSession) {
                logger.error("WebSocket transport error: ", exception);
                handleConnectionFailure(new Exception(exception));
            }
        }
    }
}