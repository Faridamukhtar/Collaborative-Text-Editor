package com.example.application.views.pageeditor.CRDT;

public class SelectionState {
    private final int start;
    private final int end;
    
    public SelectionState(int start, int end) {
        this.start = start;
        this.end = end;
    }
    
    public int getStart() { return start; }
    public int getEnd() { return end; }
}