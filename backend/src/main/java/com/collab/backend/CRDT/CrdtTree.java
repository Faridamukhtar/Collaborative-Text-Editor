package com.collab.backend.CRDT;

import java.util.*;

public class CrdtTree {
    private final CrdtNode root = new CrdtNode("root", "", null, 0, "system");
    private final Map<String, CrdtNode> nodeMap = new HashMap<>();
    private final List<String> visibleCharacterIdsInOrder = new ArrayList<>();

    public CrdtTree() {
        nodeMap.put("root", root);
    }

    public void apply(CrdtOperation op) {
        if (op.type == CrdtOperation.Type.INSERT) {
            CrdtNode newNode = new CrdtNode(op.id, op.value, op.parentId, op.timestamp, op.userId);
            CrdtNode parent = nodeMap.getOrDefault(op.parentId, root);
    
            parent.children.add(newNode);
            nodeMap.put(op.id, newNode);
    
            int insertPos = computeInsertionIndexInVisibleList(parent, newNode);
            visibleCharacterIdsInOrder.add(insertPos, op.id);
        } else if (op.type == CrdtOperation.Type.DELETE) {
            CrdtNode target = nodeMap.get(op.targetId);
            if (target != null && !target.isDeleted) {
                target.isDeleted = true;
                visibleCharacterIdsInOrder.remove(op.targetId);
            }
        }
    }

    private int computeInsertionIndexInVisibleList(CrdtNode parent, CrdtNode newNode) {
        // Step 1: find all descendants of parent in visible list (DFS subtree range)
        Set<String> subtree = new HashSet<>();
        collectVisibleSubtreeIds(parent, subtree);
    
        // Step 2: scan visibleCharacterIdsInOrder for the last node in the subtree
        int lastSubtreeIndex = -1;
        for (int i = 0; i < visibleCharacterIdsInOrder.size(); i++) {
            if (subtree.contains(visibleCharacterIdsInOrder.get(i))) {
                lastSubtreeIndex = i;
            }
        }
    
        // Insert after last known node in the parent subtree
        return (lastSubtreeIndex == -1) ? 0 : lastSubtreeIndex + 1;
    }

    private void collectVisibleSubtreeIds(CrdtNode node, Set<String> result) {
        for (CrdtNode child : node.children) {
            if (!child.isDeleted) {
                result.add(child.id);
            }
            collectVisibleSubtreeIds(child, result);
        }
    }    
    
    public void insert(String value, int pos, String userId, long timestamp) {
        String newId = userId + ":" + timestamp;
        String parentId = (pos == 0) ? "root" : visibleCharacterIdsInOrder.get(pos - 1);
        CrdtNode parent = nodeMap.getOrDefault(parentId, root);

        CrdtNode newNode = new CrdtNode(newId, value, parentId, timestamp, userId);
        parent.children.add(newNode);
        nodeMap.put(newId, newNode);
        visibleCharacterIdsInOrder.add(pos, newId);
    }

    public void delete(String id) {
        CrdtNode node = nodeMap.get(id);
        if (node != null && !node.isDeleted) {
            node.isDeleted = true;
            visibleCharacterIdsInOrder.remove(id);
        }
    }

    public String getText() {
        StringBuilder sb = new StringBuilder();
        dfs(root, sb);
        return sb.toString();
    }

    private void dfs(CrdtNode node, StringBuilder sb) {
        if (!node.id.equals("root") && !node.isDeleted) {
            sb.append(node.value);
        }
        List<CrdtNode> ordered = new ArrayList<>(node.children);
        ordered.sort(new CrdtNodeComparator());
        for (CrdtNode child : ordered) dfs(child, sb);
    }

    public List<String> getVisibleIds() {
        return new ArrayList<>(visibleCharacterIdsInOrder);
    }

    public CrdtNode getNode(String id) {
        return nodeMap.get(id);
    }
}