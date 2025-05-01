package com.example.application.views.pageeditor.socketsLogic;
import com.example.application.views.pageeditor.CRDT.*;

import com.vaadin.flow.shared.Registration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface CollaborationService {
    public CompletableFuture<String> requestInitialState();
    public boolean isConnected();
    public void connect();
    public void disconnect();
    public void sendOperation(TextOperation operation);
    public Registration subscribeToChanges(Consumer<TextOperation> listener);
}