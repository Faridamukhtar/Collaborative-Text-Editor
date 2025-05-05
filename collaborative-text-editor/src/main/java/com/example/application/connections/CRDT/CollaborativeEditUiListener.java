package com.example.application.connections.CRDT;

public interface CollaborativeEditUiListener {
    void onServerMessage(String message);
    void onReconnectionFailed();
}