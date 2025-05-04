window.initEditorConnector = function (element, userId) {
    const textarea = element.focusElement || element.querySelector("textarea");
    if (!textarea) {
        console.error("Textarea not found inside Vaadin component");
        return;
    }

    let lastValue = textarea.value;

    textarea.addEventListener("input", function (event) {
        const newValue = textarea.value;
        const cursorPos = textarea.selectionStart;
        const oldValue = lastValue;

        if (event.inputType === 'insertText' || event.inputType === 'insertFromPaste') {
            const insertedLength = newValue.length - oldValue.length;
            const inserted = newValue.slice(cursorPos - insertedLength, cursorPos);

            if (inserted && inserted.length > 0) {
                console.log(`[Insert] '${inserted}' at pos ${cursorPos - insertedLength}`);
                element.$server.onCharacterBatchInserted(inserted, cursorPos - insertedLength);
            }
        } else if (event.inputType.startsWith('delete')) {
            const deletedLength = oldValue.length - newValue.length;
            const deletePos = cursorPos;
            if (deletedLength > 0) {
                console.log(`[Delete] ${deletedLength} character(s) at pos ${deletePos}`);
                element.$server.onCharacterBatchDeleted(deletePos, deletedLength);
            }
        }

        lastValue = newValue;
    });

    window.applyRemoteChange = function (type, character, position, sourceUserId) {
        if (sourceUserId === userId) return;

        let currentValue = textarea.value;
        if (type === "insert") {
            const newValue = currentValue.slice(0, position) + character + currentValue.slice(position);
            textarea.value = newValue;
        } else if (type === "delete") {
            const newValue = currentValue.slice(0, position) + currentValue.slice(position + 1);
            textarea.value = newValue;
        } else if (type === "reset") {
            textarea.value = "";
        }
    };

    console.log("✅ Text editor connector initialized for user:", userId);
};