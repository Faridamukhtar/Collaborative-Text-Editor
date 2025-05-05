package com.collab.backend.crdt;

import java.util.Comparator;

public class CrdtNodeComparator implements Comparator<CrdtNode> {
    @Override
    public int compare(CrdtNode a, CrdtNode b) {
        int tsCompare = Long.compare(a.timestamp, b.timestamp);
        return tsCompare != 0 ? tsCompare : a.userId.compareTo(b.userId);
    }
}