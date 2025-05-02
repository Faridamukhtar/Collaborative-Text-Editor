package com.example.application.views.pageeditor.CRDT;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public class TreeBasedCRDT {
    private static final Logger logger = LoggerFactory.getLogger(TreeBasedCRDT.class);
    
    // Use concurrent collections for lock-free access
    private final ConcurrentHashMap<String, TreeNode> nodes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentSkipListSet<TreeNode>> children = new ConcurrentHashMap<>();
    private final String siteId;
    private final AtomicLong logicalTime = new AtomicLong(0);
    private final OperationBuffer buffer = new OperationBuffer();
    
    // Comparator for sorting TreeNodes
    private static final Comparator<TreeNode> NODE_COMPARATOR = 
        Comparator.comparingLong(TreeNode::getTimestamp)
                  .thenComparing(TreeNode::getSiteId);

    public TreeBasedCRDT() {
        this.siteId = UUID.randomUUID().toString();
    }

    public TreeBasedCRDT(String siteId) {
        this.siteId = siteId;
    }

    // Initialize with starting content - lock-free approach
    public void initialize(String initialContent) {
        if (initialContent == null || initialContent.isEmpty()) {
            return;
        }
        
        logger.info("Initializing with content of length: {}", initialContent.length());
        
        // Clear any existing data - no locks needed with ConcurrentHashMap
        nodes.clear();
        children.clear();
        
        TreeNode prev = null;
        for (int i = 0; i < initialContent.length(); i++) {
            char c = initialContent.charAt(i);
            String id = UUID.randomUUID().toString();
            String parentId = prev != null ? prev.getId() : null;
            TreeNode node = new TreeNode(id, parentId, logicalTime.incrementAndGet(), siteId);
            node.setValue(c);
            
            nodes.put(id, node);
            
            // Use computeIfAbsent for thread-safe initialization
            String finalParentId = parentId != null ? parentId : "root";
            children.computeIfAbsent(finalParentId, k -> new ConcurrentSkipListSet<>(NODE_COMPARATOR))
                   .add(node);
            
            prev = node;
        }
        
        logger.info("Initialization complete. Node count: {}", nodes.size());
    }

    // Insert a character at position - lock-free approach
    public TextOperation applyLocalInsert(char value, int position) {
        logger.debug("Local insert: char '{}' at position {}", value, position);
        
        // Find the node at the specified position without locks
        TreeNode parent = findNodeAt(position - 1);
        String parentId = parent != null ? parent.getId() : "root";
        String id = UUID.randomUUID().toString();
        long timestamp = logicalTime.incrementAndGet();
        
        TreeNode node = new TreeNode(id, parentId, timestamp, siteId);
        node.setValue(value);
        nodes.put(id, node);
        
        // Add to children collection using computeIfAbsent for thread safety
        children.computeIfAbsent(parentId, k -> new ConcurrentSkipListSet<>(NODE_COMPARATOR))
               .add(node);
        
        // Find the next node for context (afterPos)
        String afterPos = null;
        int currentNodeCount = getVisibleNodeCount();
        if (position < currentNodeCount) {
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

    // Delete a character at position - lock-free approach
    public TextOperation applyLocalDelete(int position) {
        logger.debug("Local delete at position {}", position);
        
        TreeNode node = findNodeAt(position);
        if (node != null) {
            // Mark as deleted - node state changes are atomic
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

    // Apply remote operation - lock-free approach
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
        
        // Update logical time to be at least as large as the operation's timestamp
        long opTimestamp = operation.getTimestamp();
        updateLogicalClock(opTimestamp);
        
        TreeNode node = new TreeNode(
            operation.getPositionId(),
            parentId,
            operation.getTimestamp(),
            operation.getSiteId()
        );
        node.setValue(operation.getCharacter());
        nodes.put(node.getId(), node);
        
        // Add to children collection
        children.computeIfAbsent(parentId, k -> new ConcurrentSkipListSet<>(NODE_COMPARATOR))
               .add(node);
        
        logger.debug("Remote insert applied: {}", operation.getPositionId());
    }
    
    private void handleRemoteDelete(TextOperation operation) {
        String nodeId = operation.getPositionId();
        TreeNode node = nodes.get(nodeId);
        if (node != null) {
            node.markDeleted();
            logger.debug("Remote delete applied: {}", nodeId);
        } else {
            logger.warn("Cannot delete node {}: not found", nodeId);
        }
    }

    // Helper method to update the logical clock in a lock-free manner
    private void updateLogicalClock(long remoteTimestamp) {
        while (true) {
            long currentTime = logicalTime.get();
            if (remoteTimestamp <= currentTime) {
                // Current time is already greater than or equal to remote timestamp
                break;
            }
            
            if (logicalTime.compareAndSet(currentTime, remoteTimestamp)) {
                // Successfully updated
                break;
            }
            
            // Another thread updated the clock, retry
        }
    }

    // Get current content - lock-free
    public String getContent() {
        StringBuilder sb = new StringBuilder();
        List<TreeNode> flatNodes = getVisibleNodes();
        
        for (TreeNode node : flatNodes) {
            sb.append(node.getValue());
        }
        
        return sb.toString();
    }
    
    // Get all visible nodes in correct order - lock-free approach
    public List<TreeNode> getVisibleNodes() {
        // Create a new list to avoid concurrent modification
        List<TreeNode> result = new ArrayList<>();
        traverseTree("root", result);
        return result;
    }

    private void traverseTree(String parentId, List<TreeNode> result) {
        // Get a snapshot of the children collection
        ConcurrentSkipListSet<TreeNode> childNodes = children.getOrDefault(parentId, new ConcurrentSkipListSet<>(NODE_COMPARATOR));
        
        // ConcurrentSkipListSet maintains sorted order based on comparator
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
        return getVisibleNodes().size();
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