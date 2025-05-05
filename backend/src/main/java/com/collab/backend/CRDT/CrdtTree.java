package com.collab.backend.crdt;

import java.util.*;
import com.collab.backend.websocket.ClientEditRequest;

public class CrdtTree {
    private final CrdtNode root = new CrdtNode("root", "", null, 0, "system");
    private final Map<String, CrdtNode> nodeMap = new HashMap<>();
    private final List<String> visibleCharacterIdsInOrder = new ArrayList<>();

    public CrdtTree() {
        nodeMap.put("root", root);
    }

    public void apply(ClientEditRequest clientEditRequest) {
        if (clientEditRequest.type == ClientEditRequest.Type.INSERT) {
            CrdtOperation op = CrdtOperation.fromClientInsert(clientEditRequest, this.getVisibleIds());
            CrdtNode newNode = new CrdtNode(op.id, op.value, op.parentId, op.timestamp, op.userId);
            CrdtNode parent = nodeMap.getOrDefault(op.parentId, root);

            parent.children.add(newNode);
            nodeMap.put(op.id, newNode);

            int insertPos = findCorrectInsertionPosition(op);
            if (insertPos < 0 || insertPos > visibleCharacterIdsInOrder.size()) {
                insertPos = clientEditRequest.position; // fallback to raw position
            }
            visibleCharacterIdsInOrder.add(insertPos, op.id);
        } else if (clientEditRequest.type == ClientEditRequest.Type.DELETE) {
            CrdtOperation op = CrdtOperation.fromClientDelete(clientEditRequest, this.getVisibleIds());
            if (clientEditRequest.position < 0 || clientEditRequest.position >= visibleCharacterIdsInOrder.size()) return;
            String idToDelete = visibleCharacterIdsInOrder.get(clientEditRequest.position);
            CrdtNode target = nodeMap.get(idToDelete);
            if (target != null && !target.isDeleted) {
                target.isDeleted = true;
                visibleCharacterIdsInOrder.remove(clientEditRequest.position);
            }
        }
    }

    private int findCorrectInsertionPosition(CrdtOperation op) {
        if (visibleCharacterIdsInOrder.isEmpty()) return 0;

        int parentIndex = -1;
        if (!"root".equals(op.parentId)) {
            parentIndex = visibleCharacterIdsInOrder.indexOf(op.parentId);
        }

        if ("root".equals(op.parentId)) {
            int pos = 0;
            while (pos < visibleCharacterIdsInOrder.size()) {
                CrdtNode node = nodeMap.get(visibleCharacterIdsInOrder.get(pos));
                if (!"root".equals(node.parentId) || compare(op, node) <= 0) break;
                pos++;
            }
            return pos;
        }

        if (parentIndex != -1) {
            int pos = parentIndex + 1;
            while (pos < visibleCharacterIdsInOrder.size()) {
                CrdtNode node = nodeMap.get(visibleCharacterIdsInOrder.get(pos));
                if (!op.parentId.equals(node.parentId) || compare(op, node) <= 0) break;
                pos++;
            }
            return pos;
        }

        CrdtNode parent = nodeMap.get(op.parentId);
        if (parent != null) {
            for (int i = 0; i < visibleCharacterIdsInOrder.size(); i++) {
                CrdtNode node = nodeMap.get(visibleCharacterIdsInOrder.get(i));
                if (isDescendantOf(node, parent)) return i;
            }
        }

        for (int i = 0; i < visibleCharacterIdsInOrder.size(); i++) {
            CrdtNode node = nodeMap.get(visibleCharacterIdsInOrder.get(i));
            if (compare(op, node) < 0) return i;
        }

        return visibleCharacterIdsInOrder.size();
    }

    private boolean isDescendantOf(CrdtNode potential, CrdtNode ancestor) {
        if (potential == null || ancestor == null) return false;
        if (potential.parentId == null) return false;
        if (potential.parentId.equals(ancestor.id)) return true;
        CrdtNode parent = nodeMap.get(potential.parentId);
        return isDescendantOf(parent, ancestor);
    }

    private int compare(CrdtOperation o1, CrdtNode o2) {
        int timeDiff = Long.compare(o1.timestamp, o2.timestamp);
        return (timeDiff != 0) ? timeDiff : o1.userId.compareTo(o2.userId);
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
        for (String id : visibleCharacterIdsInOrder) {
            CrdtNode node = nodeMap.get(id);
            if (node != null && !node.isDeleted) {
                sb.append(node.value);
            }
        }
        return sb.toString();
    }

    public List<String> getVisibleIds() {
        return new ArrayList<>(visibleCharacterIdsInOrder);
    }

    public CrdtNode getNode(String id) {
        return nodeMap.get(id);
    }

    public void clear() {
        root.children.clear();
        nodeMap.clear();
        visibleCharacterIdsInOrder.clear();
        nodeMap.put("root", root);
    }
}
