window.editorConnector = {
    editor: null,
    userId: null,
    remoteCursors: {},

    init: function(editorElement, userId) {
        console.log("[JS][INIT] Initializing editor connector for user:", userId);
        this.editor = editorElement;
        this.userId = userId;

        const quill = this.editor._quill;
        if (!quill) {
            console.error("[JS][INIT] Quill instance not found on editor");
            return;
        }

        console.log("[JS][INIT] Quill initialized, setting up listeners");

        quill.on('text-change', (delta, oldDelta, source) => {
            console.log("[JS][TEXT-CHANGE] Delta:", delta, "Source:", source);
            if (source === 'user') {
                this.editor.$server.onEditorValueChanged(
                    quill.root.innerHTML,
                    JSON.stringify(delta)
                );
            }
        });

        quill.on('selection-change', (range, oldRange, source) => {
            console.log("[JS][SELECTION] New range:", range, "Source:", source);
            if (range && source === 'user') {
                const position = this.getCursorPosition(range.index);
                console.log("[JS][SELECTION] Reporting position:", position);
                this.editor.$server.onCursorPositionChanged(position);
            }
        });
    },

    setInitialContent: function(content) {
        console.log("[JS][SYNC] Setting initial content");
        const quill = this.editor._quill;
        if (quill) {
            quill.setText(content);  // use dangerouslyPasteHTML(content) if needed
        } else {
            console.warn("[JS][SYNC] Quill instance not found while syncing content");
        }
    },

    getCursorPosition: function(index) {
        return index;
    },

    getNodeIdAtCursor: function() {
        const quill = this.editor._quill;
        const selection = quill.getSelection();
        if (!selection) {
            console.warn("[JS][NODE] No cursor selection found");
            return null;
        }

        const [leaf, offset] = quill.getLeaf(selection.index);
        if (!leaf || !leaf.parent) return "root";

        const nodeId = leaf.parent.domNode.getAttribute('data-node-id') ||
                       this.generateNodeId(leaf.parent);

        console.log("[JS][NODE] Node ID at cursor:", nodeId);
        return nodeId;
    },

    generateNodeId: function(node) {
        const nodeId = "node-" + Math.random().toString(36).substring(2, 15);
        if (node.domNode) {
            node.domNode.setAttribute('data-node-id', nodeId);
        }
        console.log("[JS][NODE] Generated new node ID:", nodeId);
        return nodeId;
    },

    parseDelta: function(deltaStr) {
        const delta = JSON.parse(deltaStr);
        let operation = {};
        console.log("[JS][DELTA] Parsing delta:", delta);

        if (delta.ops && delta.ops.some(op => op.insert)) {
            const insertOp = delta.ops.find(op => op.insert);
            operation = {
                type: "insert",
                position: delta.ops[0].retain || 0,
                content: insertOp.insert
            };
        } else if (delta.ops && delta.ops.some(op => op.delete)) {
            const deleteOp = delta.ops.find(op => op.delete);
            const position = delta.ops[0].retain || 0;
            const quill = this.editor._quill;
            const [leaf] = quill.getLeaf(position);
            const nodeId = leaf.parent.domNode.getAttribute('data-node-id') ||
                           this.generateNodeId(leaf.parent);
            operation = {
                type: "delete",
                nodeId: nodeId
            };
        } else if (delta.ops && delta.ops.some(op => op.attributes)) {
            const quill = this.editor._quill;
            const position = delta.ops[0].retain || 0;
            const [leaf] = quill.getLeaf(position);
            const nodeId = leaf.parent.domNode.getAttribute('data-node-id') ||
                           this.generateNodeId(leaf.parent);
            operation = {
                type: "update",
                nodeId: nodeId,
                content: JSON.stringify(delta.ops.find(op => op.attributes))
            };
        }

        console.log("[JS][DELTA] Parsed operation object:", operation);
        return JSON.stringify(operation);
    },

    applyInsert: function(parentId, position, content) {
        console.log("[JS][APPLY] INSERT", { parentId, position, content });
        const quill = this.editor._quill;
        let index = position;

        if (parentId !== "root") {
            const parentNode = document.querySelector(`[data-node-id="${parentId}"]`);
            if (parentNode) {
                const parentIndex = quill.getIndex(quill.getLeaf(parentNode)[0]);
                if (parentIndex !== undefined) {
                    index = parentIndex + position;
                }
            }
        }

        quill.insertText(index, content, 'api');
    },

    applyUpdate: function(nodeId, content) {
        console.log("[JS][APPLY] UPDATE", { nodeId, content });
        const quill = this.editor._quill;
        const node = document.querySelector(`[data-node-id="${nodeId}"]`);
        if (!node) return;

        const nodeIndex = quill.getIndex(quill.getLeaf(node)[0]);
        if (nodeIndex === undefined) return;

        try {
            const formatData = JSON.parse(content);
            const length = node.textContent.length;
            Object.keys(formatData).forEach(format => {
                quill.formatText(nodeIndex, length, format, formatData[format], 'api');
            });
        } catch (e) {
            console.error("[JS][APPLY] Error applying update:", e);
        }
    },

    applyDelete: function(nodeId) {
        console.log("[JS][APPLY] DELETE", nodeId);
        const quill = this.editor._quill;
        const node = document.querySelector(`[data-node-id="${nodeId}"]`);
        if (!node) return;

        const nodeIndex = quill.getIndex(quill.getLeaf(node)[0]);
        if (nodeIndex === undefined) return;

        const length = node.textContent.length;
        quill.deleteText(nodeIndex, length, 'api');
    },

    showRemoteCursor: function(userId, nodeId, offset) {
        if (userId === this.userId) return;

        this.removeRemoteCursor(userId);
        const quill = this.editor._quill;

        const cursor = document.createElement('div');
        cursor.className = 'remote-cursor';
        cursor.setAttribute('data-user-id', userId);
        cursor.style.position = 'absolute';
        cursor.style.height = '20px';
        cursor.style.width = '2px';
        cursor.style.backgroundColor = this.getUserColor(userId);

        const label = document.createElement('div');
        label.className = 'remote-cursor-label';
        label.textContent = userId;
        label.style.backgroundColor = this.getUserColor(userId);
        label.style.color = 'white';
        label.style.padding = '2px 4px';
        label.style.borderRadius = '3px';
        label.style.fontSize = '10px';
        label.style.position = 'absolute';
        label.style.top = '-20px';
        label.style.whiteSpace = 'nowrap';

        cursor.appendChild(label);

        const node = document.querySelector(`[data-node-id="${nodeId}"]`);
        if (node) {
            const nodeIndex = quill.getIndex(quill.getLeaf(node)[0]);
            if (nodeIndex !== undefined) {
                const position = quill.getBounds(nodeIndex + offset);
                cursor.style.left = position.left + 'px';
                cursor.style.top = position.top + 'px';
                quill.container.appendChild(cursor);
                this.remoteCursors[userId] = cursor;
                console.log("[JS][CURSOR] Remote cursor shown for user:", userId);
            }
        }
    },

    removeRemoteCursor: function(userId) {
        if (this.remoteCursors[userId]) {
            this.remoteCursors[userId].remove();
            delete this.remoteCursors[userId];
            console.log("[JS][CURSOR] Removed remote cursor for user:", userId);
        }
    },

    getUserColor: function(userId) {
        let hash = 0;
        for (let i = 0; i < userId.length; i++) {
            hash = userId.charCodeAt(i) + ((hash << 5) - hash);
        }

        const r = Math.min(Math.max(((hash & 0xFF0000) >> 16), 100), 200);
        const g = Math.min(Math.max(((hash & 0x00FF00) >> 8), 100), 200);
        const b = Math.min(Math.max((hash & 0x0000FF), 100), 200);

        return `rgb(${r}, ${g}, ${b})`;
    }
};

// Auto-initialize safely
window.initEditorConnector = function(editorElement, userId) {
    console.log("[JS][AUTO] Calling initEditorConnector from Java executeJs()");
    window.editorConnector.init(editorElement, userId);
};
