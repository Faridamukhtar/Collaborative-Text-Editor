package com.collab.backend.CRDT;

import java.util.Comparator;

public class CrdtNodeComparator implements Comparator<CrdtNode> {
    @Override
    public int compare(CrdtNode n1, CrdtNode n2) {
        int t = Long.compare(n2.timestamp, n1.timestamp); // Descending
        return (t != 0) ? t : n1.userId.compareTo(n2.userId);
    }
}