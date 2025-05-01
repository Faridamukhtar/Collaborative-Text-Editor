package com.example.application.views.pageeditor.CRDT;

import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;

public class ClientRGA {
    private final NavigableMap<ClientPosition, Character> elements = new ConcurrentSkipListMap<>();
    private String clientId = UUID.randomUUID().toString();
    private final OperationBuffer buffer = new OperationBuffer();
    private long sequenceCounter = 0;

    public ClientRGA() {
        // Initialize with empty state
    }
    // Initialize with initial content
    public ClientRGA(String initialContent) {
        for (int i = 0; i < initialContent.length(); i++) {
            ClientPosition pos = new ClientPosition(clientId, ++sequenceCounter, Collections.singletonList(i));
            elements.put(pos, initialContent.charAt(i));
        }
    }
    // Initialize with initial content
    public void initialize(String initialContent, String clientId) {
        this.clientId = clientId;
        for (int i = 0; i < initialContent.length(); i++) {
            ClientPosition pos = new ClientPosition(clientId, ++sequenceCounter, Collections.singletonList(i));
            elements.put(pos, initialContent.charAt(i));
        }
    }

    // Apply local insert operation
    public synchronized TextOperation applyLocalInsert(int index, char value) {
        ClientPosition previous = findPositionBefore(index);
        ClientPosition newPos = generatePosition(previous);
        TextOperation op = TextOperation.createInsert(
            value,
            newPos.toString(),
            System.currentTimeMillis(),
            clientId,
            previous != null ? previous.toString() : null,
            findPositionAfter(previous) != null ? findPositionAfter(previous).toString() : null
        );
        
        buffer.add(op);
        elements.put(newPos, value);
        return op;
    }

    // Apply local delete operation
    public synchronized TextOperation applyLocalDelete(int index) {
        ClientPosition pos = findPositionAt(index);
        if (pos != null) {
            TextOperation op = TextOperation.createDelete(
                pos.toString(),
                System.currentTimeMillis(),
                clientId
            );
            buffer.add(op);
            elements.remove(pos);
            return op;
        }
        return null;
    }

    // Apply local operation
    public synchronized void applyLocal(TextOperation localOp) {
        switch (localOp.getType()) {
            case INSERT:
                applyLocalInsert(
                    Integer.parseInt(localOp.getPositionId()),
                    localOp.getCharacter()
                );   
                break;
            case DELETE:
                applyLocalDelete(
                    Integer.parseInt(localOp.getPositionId())
                );
                break;
        }
    }
        

    // Apply remote operation
    public synchronized void applyRemote(TextOperation remoteOp) {
        ClientPosition remotePos = ClientPosition.fromString(remoteOp.getPositionId());
        ClientPosition transformedPos = transformPosition(remotePos, remoteOp.getTimestamp());
        
        switch (remoteOp.getType()) {
            case INSERT:
                elements.put(transformedPos, remoteOp.getCharacter());
                break;
            case DELETE:
                elements.remove(transformedPos);
                break;
        }
    }

    // Helper methods
    private ClientPosition findPositionBefore(int index) {
        if (index <= 0) return null;
        List<ClientPosition> positions = new ArrayList<>(elements.keySet());
        return index <= positions.size() ? positions.get(index - 1) : null;
    }

    private ClientPosition findPositionAt(int index) {
        List<ClientPosition> positions = new ArrayList<>(elements.keySet());
        return index >= 0 && index < positions.size() ? positions.get(index) : null;
    }

    private ClientPosition findPositionAfter(ClientPosition pos) {
        return pos == null ? elements.firstKey() : elements.higherKey(pos);
    }

    private ClientPosition generatePosition(ClientPosition previous) {
        ClientPosition next = findPositionAfter(previous);
        List<Integer> newFractional = generateFractionalIndex(
            previous != null ? previous.getFractionalIndex() : Collections.emptyList(),
            next != null ? next.getFractionalIndex() : Collections.emptyList()
        );
        return new ClientPosition(clientId, ++sequenceCounter, newFractional);
    }

    private ClientPosition transformPosition(ClientPosition remotePos, long remoteTimestamp) {
        ClientPosition transformed = remotePos;
        for (TextOperation localOp : buffer.getPendingOperations()) {
            if (localOp.getTimestamp() < remoteTimestamp) {
                ClientPosition localPos = ClientPosition.fromString(localOp.getPositionId());
                if (localOp.getType() == TextOperation.Type.INSERT && 
                    localPos.compareTo(remotePos) < 0) {
                    transformed = new ClientPosition(
                        transformed.getSiteId(),
                        transformed.getSeq(),
                        generateFractionalIndex(
                            transformed.getFractionalIndex(),
                            localPos.getFractionalIndex()
                        )
                    );
                }
            }
        }
        return transformed;
    }

    private List<Integer> generateFractionalIndex(List<Integer> prev, List<Integer> next) {
        List<Integer> newIndex = new ArrayList<>();
        int level = 0;
        while (true) {
            int prevDigit = level < prev.size() ? prev.get(level) : 0;
            int nextDigit = level < next.size() ? next.get(level) : prevDigit + 2;
            
            if (nextDigit - prevDigit > 1) {
                newIndex.add(prevDigit + 1);
                break;
            } else {
                newIndex.add(prevDigit);
                level++;
            }
        }
        return newIndex;
    }

    public String getContent() {
        StringBuilder sb = new StringBuilder();
        elements.values().forEach(sb::append);
        return sb.toString();
    }
    
    public String getClientId() {
        return clientId;
    }

    public int length() {
        return elements.size();
    }

    public void acknowledge(String positionId) {
        buffer.acknowledge(positionId);
    }
}