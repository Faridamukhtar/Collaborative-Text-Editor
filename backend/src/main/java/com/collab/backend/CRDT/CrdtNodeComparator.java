package com.collab.backend.crdt;

import java.util.Comparator;

public class CrdtNodeComparator implements Comparator<CrdtNode> {
    @Override
    public int compare(CrdtNode n1, CrdtNode n2) {
        // Compare by timestamp in ascending order (older edits first)
        int timestampComparison = Long.compare(n1.timestamp, n2.timestamp);

        if (timestampComparison != 0) {
            return timestampComparison;
        }

        // If timestamps are equal, compare lexicographically by userId
        return n1.userId.compareTo(n2.userId);
    }
}
