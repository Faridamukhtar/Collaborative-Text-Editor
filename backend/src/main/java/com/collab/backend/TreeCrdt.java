
package com.collab.backend;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of a Tree CRDT (Conflict-free Replicated Data Type)
 * for collaborative document editing
 */
@Component
public class TreeCrdt {
    private final DocumentNode root;
    private final Map<String, DocumentNode> nodeMap;
    
    public TreeCrdt() {
        this.root = new DocumentNode("root", "system");
        this.nodeMap = new ConcurrentHashMap<>();
        nodeMap.put(root.getId(), root);
    }
    
    /**
     * Apply an operation to the CRDT
     * @param operation The operation to apply
     * @return true if the operation was applied successfully
     */
    public synchronized boolean applyOperation(Operation operation) {
        switch (operation.getType()) {
            case INSERT:
                return handleInsert(operation);
            case UPDATE:
                return handleUpdate(operation);
            case DELETE:
                return handleDelete(operation);
            default:
                return false;
        }
    }
    
    private boolean handleInsert(Operation operation) {
        String parentId = operation.getParentId();
        DocumentNode parent = nodeMap.get(parentId);
        
        if (parent == null) {
            return false;
        }
        
        DocumentNode newNode = new DocumentNode(operation.getContent(), operation.getUserId());
        nodeMap.put(newNode.getId(), newNode);
        
        int position = Math.min(operation.getPosition(), parent.getChildren().size());
        parent.addChildAt(newNode, position);
        
        return true;
    }
    
    private boolean handleUpdate(Operation operation) {
        DocumentNode node = nodeMap.get(operation.getNodeId());
        
        if (node == null || node.isTombstone()) {
            return false;
        }
        
        node.setContent(operation.getContent());
        return true;
    }
    
    private boolean handleDelete(Operation operation) {
        DocumentNode node = nodeMap.get(operation.getNodeId());
        
        if (node == null || node.isTombstone() || node == root) {
            return false;
        }
        
        node.markAsDeleted();
        return true;
    }
    
    /**
     * Get the document as a string
     * @return The document content
     */
    public String getDocumentContent() {
        StringBuilder sb = new StringBuilder();
        buildContent(root, sb);
        return sb.toString();
    }
    
    private void buildContent(DocumentNode node, StringBuilder sb) {
        if (node != root && !node.isTombstone()) {
            sb.append(node.getContent());
        }
        
        for (DocumentNode child : node.getChildren()) {
            if (!child.isTombstone()) {
                buildContent(child, sb);
            }
        }
    }

    /**
     * Get a node by its ID
     * @param nodeId The ID of the node
     * @return The node, or null if not found
     */
    public DocumentNode getNode(String nodeId) {
        return nodeMap.get(nodeId);
    }

    /**
     * Get the root node
     * @return The root node
     */
    public DocumentNode getRoot() {
        return root;
    }
}