package com.example.application.views.pageeditor.CRDT;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class TreeBasedCRDT {
    private static final Logger logger = LoggerFactory.getLogger(TreeBasedCRDT.class);
    
    private final Map<String, TreeNode> nodes = new ConcurrentHashMap<>();
    private final Map<String, List<TreeNode>> children = new ConcurrentHashMap<>();
    private final String siteId;
    private final AtomicLong logicalTime = new AtomicLong(0);
    private final OperationBuffer buffer = new OperationBuffer();

    public TreeBasedCRDT() {
        this.siteId = UUID.randomUUID().toString();
    }

    public TreeBasedCRDT(String siteId) {
        this.siteId = siteId;
    }

    // Initialize with starting content
    public void initialize(String initialContent) {
        if (initialContent == null || initialContent.isEmpty()) {
            return;
        }
        
        logger.info("Initializing with content of length: {}", initialContent.length());
        
        TreeNode prev = null;
        for (int i = 0; i < initialContent.length(); i++) {
            char c = initialContent.charAt(i);
            String id = UUID.randomUUID().toString();
            String parentId = prev != null ? prev.getId() : null;
            TreeNode node = new TreeNode(id, parentId, logicalTime.incrementAndGet(), siteId);
            node.setValue(c);
            
            nodes.put(id, node);
            if (parentId != null) {
                children.computeIfAbsent(parentId, k -> new ArrayList<>()).add(node);
            } else {
                children.computeIfAbsent("root", k -> new ArrayList<>()).add(node);
            }
            prev = node;
        }
        
        logger.info("Initialization complete. Node count: {}", nodes.size());
    }

    // Insert a character at position
    public TextOperation applyLocalInsert(char value, int position) {
        logger.debug("Local insert: char '{}' at position {}", value, position);
        
        TreeNode parent = findNodeAt(position - 1);
        String parentId = parent != null ? parent.getId() : "root";
        String id = UUID.randomUUID().toString();
        long timestamp = logicalTime.incrementAndGet();
        
        TreeNode node = new TreeNode(id, parentId, timestamp, siteId);
        node.setValue(value);
        nodes.put(id, node);
        
        List<TreeNode> siblingNodes = children.computeIfAbsent(parentId, k -> new ArrayList<>());
        siblingNodes.add(node);
        
        // Find the next node for context (afterPos)
        String afterPos = null;
        if (position < getVisibleNodeCount()) {
            TreeNode nextNode = findNodeAt(position);
            if (nextNode != null) {
                afterPos = nextNode.getId();
            }
        }
        
        TextOperation op = TextOperation.createInsert(
            value,
            id,
            timestamp,
            siteId,
            parentId,
            afterPos
        );
        
        buffer.add(op);
        logger.debug("Created operation: {}", op);
        return op;
    }

    // Delete a character at position
    public TextOperation applyLocalDelete(int position) {
        logger.debug("Local delete at position {}", position);
        
        TreeNode node = findNodeAt(position);
        if (node != null) {
            node.markDeleted();
            
            TextOperation op = TextOperation.createDelete(
                node.getId(),
                logicalTime.incrementAndGet(),
                siteId
            );
            
            buffer.add(op);
            logger.debug("Created delete operation: {}", op);
            return op;
        }
        
        logger.warn("No node found at position {} to delete", position);
        return null;
    }

    // Apply remote operation
    public void applyRemote(TextOperation operation) {
        logger.debug("Applying remote operation: {}", operation);
        
        switch (operation.getType()) {
            case INSERT:
                handleRemoteInsert(operation);
                break;
                
            case DELETE:
                handleRemoteDelete(operation);
                break;
                
            default:
                logger.warn("Unknown operation type: {}", operation.getType());
        }
    }
    
    private void handleRemoteInsert(TextOperation operation) {
        String parentId = operation.getBeforePos();
        if (parentId == null) {
            parentId = "root";
        }
        
        // Check if node already exists (idempotency)
        if (nodes.containsKey(operation.getPositionId())) {
            logger.debug("Node {} already exists, ignoring insert", operation.getPositionId());
            return;
        }
        
        TreeNode node = new TreeNode(
            operation.getPositionId(),
            parentId,
            operation.getTimestamp(),
            operation.getSiteId()
        );
        node.setValue(operation.getCharacter());
        nodes.put(node.getId(), node);
        
        // Add to children list
        children.computeIfAbsent(parentId, k -> new ArrayList<>()).add(node);
        
        // Use afterPos for ordering hints if provided
        if (operation.getAfterPos() != null && children.containsKey(parentId)) {
            List<TreeNode> siblings = children.get(parentId);
            siblings.sort(Comparator.naturalOrder());
        }
        
        logger.debug("Remote insert applied: {}", operation.getPositionId());
    }
    
    private void handleRemoteDelete(TextOperation operation) {
        String nodeId = operation.getPositionId();
        if (nodes.containsKey(nodeId)) {
            nodes.get(nodeId).markDeleted();
            logger.debug("Remote delete applied: {}", nodeId);
        } else {
            logger.warn("Cannot delete node {}: not found", nodeId);
        }
    }

    // Get current content
    public String getContent() {
        StringBuilder sb = new StringBuilder();
        List<TreeNode> flatNodes = getVisibleNodes();
        
        for (TreeNode node : flatNodes) {
            sb.append(node.getValue());
        }
        
        return sb.toString();
    }
    
    // Get all visible nodes in correct order
    public List<TreeNode> getVisibleNodes() {
        List<TreeNode> result = new ArrayList<>();
        traverseTree("root", result);
        return result;
    }

    private void traverseTree(String parentId, List<TreeNode> result) {
        List<TreeNode> childNodes = children.getOrDefault(parentId, new ArrayList<>());
        // Sort nodes by timestamp and siteId for consistent ordering
        childNodes.sort(Comparator.comparingLong(TreeNode::getTimestamp)
                .thenComparing(TreeNode::getSiteId));
        
        for (TreeNode node : childNodes) {
            if (!node.isDeleted()) {
                result.add(node);
            }
            // Continue traversal with this node's children
            traverseTree(node.getId(), result);
        }
    }

    private TreeNode findNodeAt(int position) {
        List<TreeNode> flatList = getVisibleNodes();
        return position >= 0 && position < flatList.size() ? flatList.get(position) : null;
    }
    
    private int getVisibleNodeCount() {
        return (int) getVisibleNodes().stream().filter(n -> !n.isDeleted()).count();
    }

    public void acknowledge(String operationId) {
        buffer.acknowledge(operationId);
    }
    
    public String getSiteId() {
        return siteId;
    }
    
    public int length() {
        return getVisibleNodeCount();
    }
}