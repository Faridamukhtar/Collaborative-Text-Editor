package com.example.application.views.pageeditor.CRDT;

import java.io.Serializable;

/**
 * Represents a node in the tree-based CRDT structure.
 * Each node contains a character and metadata about the insertion.
 */
public class TreeNode implements Comparable<TreeNode>, Serializable {
    private static final long serialVersionUID = 1L;
    
    private final String id;
    private final String parentId;
    private final long timestamp;
    private final String siteId;
    private char value;
    private boolean deleted;

    /**
     * Create a new tree node
     * 
     * @param id Unique identifier for this node
     * @param parentId ID of the parent node
     * @param timestamp Logical timestamp when this node was created
     * @param siteId ID of the site that created this node
     */
    public TreeNode(String id, String parentId, long timestamp, String siteId) {
        this.id = id;
        this.parentId = parentId;
        this.timestamp = timestamp;
        this.siteId = siteId;
        this.deleted = false;
    }

    /**
     * Get the unique identifier for this node
     */
    public String getId() {
        return id;
    }

    /**
     * Get the parent node's identifier
     */
    public String getParentId() {
        return parentId;
    }

    /**
     * Get the logical timestamp when this node was created
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Get the site ID that created this node
     */
    public String getSiteId() {
        return siteId;
    }

    /**
     * Get the character value stored in this node
     */
    public char getValue() {
        return value;
    }

    /**
     * Set the character value for this node
     */
    public void setValue(char value) {
        this.value = value;
    }

    /**
     * Check if this node has been marked as deleted
     */
    public boolean isDeleted() {
        return deleted;
    }

    /**
     * Mark this node as deleted
     */
    public void markDeleted() {
        this.deleted = true;
    }

    /**
     * Compare this node with another for ordering in the tree
     * First compares by timestamp, then by site ID for consistency
     */
    @Override
    public int compareTo(TreeNode other) {
        int timeCompare = Long.compare(this.timestamp, other.timestamp);
        if (timeCompare != 0) {
            return timeCompare;
        }
        return this.siteId.compareTo(other.siteId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TreeNode treeNode = (TreeNode) o;
        return id.equals(treeNode.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "TreeNode{" +
                "id='" + id + '\'' +
                ", parentId='" + parentId + '\'' +
                ", timestamp=" + timestamp +
                ", siteId='" + siteId + '\'' +
                ", value=" + value +
                ", deleted=" + deleted +
                '}';
    }
}