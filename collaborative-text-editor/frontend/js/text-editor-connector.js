window.initEditorConnector = function (element, userId) {
    const textarea = element.focusElement || element.querySelector("textarea");
    if (!textarea) {
        console.error("Textarea not found inside Vaadin component");
        return;
    }

    let lastValue = textarea.value;
    let suppressInput = false;

    // Allow backend to suppress frontend input listeners
    window.suppressInputStart = () => suppressInput = true;
    window.suppressInputEnd = () => suppressInput = false;

    textarea.addEventListener("input", function (event) {
        if (suppressInput) {
            console.log("🔇 Suppressed input event due to backend update");
            lastValue = textarea.value; // keep local value in sync
            return;
        }

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

    console.log("✅ Text editor connector initialized for user:", userId);
};
