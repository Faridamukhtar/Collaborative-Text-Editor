package com.example.application.views.pageeditor.CRDT;

import java.util.*;
import java.util.stream.Collectors;

public class ClientPosition implements Comparable<ClientPosition> {
    private final String siteId;
    private final long seq;
    private final List<Integer> fractionalIndex;

    public ClientPosition(String siteId, long seq) {
        this(siteId, seq, Collections.emptyList());
    }

    public ClientPosition(String siteId, long seq, List<Integer> fractionalIndex) {
        this.siteId = siteId;
        this.seq = seq;
        this.fractionalIndex = new ArrayList<>(fractionalIndex);
    }

    public static ClientPosition fromString(String str) {
        String[] parts = str.split(":");
        String siteId = parts[0];
        long seq = Long.parseLong(parts[1]);
        List<Integer> fractionalIndex = parts.length > 2 ?
            Arrays.stream(parts[2].split(","))
                  .map(Integer::parseInt)
                  .collect(Collectors.toList()) :
            Collections.emptyList();
        return new ClientPosition(siteId, seq, fractionalIndex);
    }

    @Override
    public String toString() {
        return siteId + ":" + seq + 
               (fractionalIndex.isEmpty() ? "" : ":" + 
                fractionalIndex.stream()
                               .map(String::valueOf)
                               .collect(Collectors.joining(",")));
    }

    @Override
    public int compareTo(ClientPosition other) {
        int seqCompare = Long.compare(this.seq, other.seq);
        if (seqCompare != 0) return seqCompare;
        
        int siteCompare = this.siteId.compareTo(other.siteId);
        if (siteCompare != 0) return siteCompare;
        
        for (int i = 0; i < Math.min(fractionalIndex.size(), other.fractionalIndex.size()); i++) {
            int cmp = Integer.compare(fractionalIndex.get(i), other.fractionalIndex.get(i));
            if (cmp != 0) return cmp;
        }
        return Integer.compare(fractionalIndex.size(), other.fractionalIndex.size());
    }

    // Getters
    public String getSiteId() { return siteId; }
    public long getSeq() { return seq; }
    public List<Integer> getFractionalIndex() { return Collections.unmodifiableList(fractionalIndex); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClientPosition that = (ClientPosition) o;
        return seq == that.seq && 
               siteId.equals(that.siteId) && 
               fractionalIndex.equals(that.fractionalIndex);
    }

    @Override
    public int hashCode() {
        return Objects.hash(siteId, seq, fractionalIndex);
    }
}