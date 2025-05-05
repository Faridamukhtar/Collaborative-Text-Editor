package com.collab.backend.crdt;

import java.util.*;
import com.collab.backend.websocket.ClientEditRequest;

public class CrdtTree {
    private final Map<String, CrdtNode> nodeMap = new HashMap<>();
    private List<String> visibleCharacterIdsInOrder = new ArrayList<>();
    private boolean visibleIdsNeedRefresh = true;

    // Linked-list entry point
    private CrdtNode firstNode = null;

    public CrdtTree() {
    }

    public void apply(ClientEditRequest req) {
        if (req.type == ClientEditRequest.Type.INSERT) {
            CrdtOperation op = CrdtOperation.fromClientInsert(req, getVisibleIds());
            applyInsertOperation(op);
        } else if (req.type == ClientEditRequest.Type.DELETE) {
            if (req.position < 0 || req.position >= getVisibleIds().size()) {
                System.err.println("❌ Invalid delete position: " + req.position);
                return;
            }
            String idToDelete = getVisibleIds().get(req.position);
            applyDeleteOperation(idToDelete);
        }

        System.err.println("✅ Updated text: " + getText());
    }

    public void applyInsertOperation(CrdtOperation op) {
        if (nodeMap.containsKey(op.id))
            return;

        CrdtNode item = new CrdtNode(op.id, op.value, op.parentId, op.timestamp, op.userId);
        nodeMap.put(item.id, item);

        if (firstNode == null) {
            firstNode = item;
            return;
        }

        if (op.parentId.equals("root")) {
            // Insert before first if item is earlier
            if (compare(item, firstNode) < 0) {
                item.right = firstNode;
                firstNode.left = item;
                firstNode = item;
                return;
            }

            CrdtNode current = firstNode;
            while (current.right != null && compare(current.right, item) < 0) {
                current = current.right;
            }

            item.right = current.right;
            if (current.right != null)
                current.right.left = item;
            current.right = item;
            item.left = current;
        } else {
            CrdtNode left = nodeMap.get(op.parentId);
            if (left == null) {
                left = firstNode;
            }

            item.left = left;
            item.right = left != null ? left.right : null;
            if (left != null)
                left.right = item;
            if (item.right != null)
                item.right.left = item;
        }

        visibleIdsNeedRefresh = true;
    }

    public void applyDeleteOperation(String targetId) {
        CrdtNode target = nodeMap.get(targetId);
        if (target != null && !target.isDeleted) {
            target.isDeleted = true;
            visibleIdsNeedRefresh = true;
            System.err.println("🗑️ Deleted: " + targetId);
        }
    }

    public String getText() {
        StringBuilder sb = new StringBuilder();
        CrdtNode current = firstNode;
        while (current != null) {
            if (!current.isDeleted) {
                sb.append(current.value);
            }
            current = current.right;
        }
        return sb.toString();
    }

    public List<String> getVisibleIds() {
        if (visibleIdsNeedRefresh) {
            computeVisibleIdsInOrder();
            visibleIdsNeedRefresh = false;
        }
        return new ArrayList<>(visibleCharacterIdsInOrder);
    }

    private void computeVisibleIdsInOrder() {
        List<String> result = new ArrayList<>();
        CrdtNode current = firstNode;
        while (current != null) {
            if (!current.isDeleted) {
                result.add(current.id);
            }
            current = current.right;
        }
        visibleCharacterIdsInOrder = result;
    }

    public void insert(String value, int position, String userId, long timestamp) {
        ClientEditRequest req = new ClientEditRequest();
        req.type = ClientEditRequest.Type.INSERT;
        req.position = position;
        req.value = value;
        req.userId = userId;
        req.timestamp = timestamp;
        apply(req);
    }

    public void delete(int position, String userId) {
        if (position < 0 || position >= getVisibleIds().size()) {
            System.err.println("❌ Invalid delete position: " + position);
            return;
        }

        ClientEditRequest req = new ClientEditRequest();
        req.type = ClientEditRequest.Type.DELETE;
        req.position = position;
        req.userId = userId;
        req.timestamp = System.currentTimeMillis();
        apply(req);
    }

    private int compare(CrdtNode a, CrdtNode b) {
        int tsCompare = Long.compare(a.timestamp, b.timestamp);
        return tsCompare != 0 ? tsCompare : a.userId.compareTo(b.userId);
    }

    public void clear() {
        firstNode = null;
        nodeMap.clear();
        visibleCharacterIdsInOrder.clear();
        visibleIdsNeedRefresh = true;
    }
}
