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
            visibleCharacterIdsInOrder.add(insertPos, op.id);
        } else if (clientEditRequest.type == ClientEditRequest.Type.DELETE) {
            CrdtOperation op = CrdtOperation.fromClientDelete(clientEditRequest, this.getVisibleIds());
            CrdtNode target = nodeMap.get(op.targetId);
            if (target != null && !target.isDeleted) {
                target.isDeleted = true;
                visibleCharacterIdsInOrder.remove(op.targetId);
            }
        }
    }

    private int findCorrectInsertionPosition(CrdtOperation op) {
        if (visibleCharacterIdsInOrder.isEmpty()) return 0;

        if ("root".equals(op.parentId)) {
            for (int i = visibleCharacterIdsInOrder.size() - 1; i >= 0; i--) {
                CrdtNode node = nodeMap.get(visibleCharacterIdsInOrder.get(i));
                if ("root".equals(node.parentId)) {
                    if (compare(op, node) > 0) {
                        return i + 1;
                    }
                }
            }
            return 0;
        }

        for (int i = 0; i < visibleCharacterIdsInOrder.size(); i++) {
            CrdtNode node = nodeMap.get(visibleCharacterIdsInOrder.get(i));
            if (op.parentId.equals(node.parentId)) {
                if (compare(op, node) < 0) {
                    return i;
                }
            }
        }

        for (int i = visibleCharacterIdsInOrder.size() - 1; i >= 0; i--) {
            CrdtNode node = nodeMap.get(visibleCharacterIdsInOrder.get(i));
            if (op.parentId.equals(node.parentId)) {
                return i + 1;
            }
        }

        return visibleCharacterIdsInOrder.size();
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
        dfs(root, sb);
        return sb.toString();
    }

    private void dfs(CrdtNode node, StringBuilder sb) {
        if (!node.id.equals("root") && !node.isDeleted) {
            sb.append(node.value);
        }
        List<CrdtNode> ordered = new ArrayList<>(node.children);
        ordered.sort(new CrdtNodeComparator());
        for (CrdtNode child : ordered)
            dfs(child, sb);
    }

    public List<String> getVisibleIds() {
        return new ArrayList<>(visibleCharacterIdsInOrder);
    }

    public CrdtNode getNode(String id) {
        return nodeMap.get(id);
    }
}
