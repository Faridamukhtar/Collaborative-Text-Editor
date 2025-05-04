window.initEditorConnector = function (element, userId) {
    const textarea = element.focusElement || element.querySelector("textarea");
    if (!textarea) {
        console.error("Textarea not found inside Vaadin component");
        return;
    }

    let lastValue = textarea.value;
    let isRemoteUpdate = false; // Flag to track server-originated updates

    // Handle local edits
    textarea.addEventListener("input", function (event) {
        if (isRemoteUpdate) {
            isRemoteUpdate = false;
            return; // Skip processing for server-initiated updates
        }

        const newValue = textarea.value;
        const cursorPos = textarea.selectionStart;
        const oldValue = lastValue;

        if (event.inputType === 'insertText' || event.inputType === 'insertFromPaste') {
            const insertedLength = newValue.length - oldValue.length;
            const inserted = newValue.slice(cursorPos - insertedLength, cursorPos);

            if (inserted && inserted.length > 0) {
                console.log(`[Local Insert] '${inserted}' at pos ${cursorPos - insertedLength}`);
                element.$server.onCharacterBatchInserted(inserted, cursorPos - insertedLength);
            }
        } else if (event.inputType.startsWith('delete')) {
            const deletedLength = oldValue.length - newValue.length;
            const deletePos = cursorPos;
            if (deletedLength > 0) {
                console.log(`[Local Delete] ${deletedLength} character(s) at pos ${deletePos}`);
                element.$server.onCharacterBatchDeleted(deletePos, deletedLength);
            }
        }

        lastValue = newValue;
    });

    // Handle server updates
    element.$server.updateContent = function (newContent, cursorPosition) {
        isRemoteUpdate = true;
        console.log(`[Remote Update] Applying update: '${newContent}'`);
        
        // Save current selection
        const prevSelectionStart = textarea.selectionStart;
        const prevSelectionEnd = textarea.selectionEnd;
        
        // Apply update
        textarea.value = newContent;
        lastValue = newContent;
        
        // Restore cursor position if this isn't our own update
        if (cursorPosition !== undefined) {
            textarea.selectionStart = cursorPosition;
            textarea.selectionEnd = cursorPosition;
        } else {
            textarea.selectionStart = prevSelectionStart;
            textarea.selectionEnd = prevSelectionEnd;
        }
    };

    // Handle cursor position updates from other users
    element.$server.updateCursorPosition = function (userId, position) {
        // Implement cursor visualization for other users
        console.log(`[Cursor Update] User ${userId} moved to position ${position}`);
        // You'll need to add UI elements to show other users' cursors
    };

    console.log("✅ Text editor connector initialized for user:", userId);
};