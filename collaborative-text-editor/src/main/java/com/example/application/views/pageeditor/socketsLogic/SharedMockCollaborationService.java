package com.example.application.views.pageeditor.socketsLogic;
import com.example.application.views.pageeditor.CRDT.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A shared service that connects multiple MockCollaborationService2 instances
 * to enable collaboration between them.
 */
public class SharedMockCollaborationService {
    private final List<MockCollaborationService2> connectedServices = new ArrayList<>();
    private String sharedContent = "";
    
    /**
     * Create a new shared service with initial content
     * @param initialContent The initial document content
     */
    public SharedMockCollaborationService(String initialContent) {
        this.sharedContent = initialContent;
    }
    
    /**
     * Register a MockCollaborationService2 with this shared service
     * @param service The service to register
     */
    public void registerService(MockCollaborationService2 service) {
        connectedServices.add(service);
        service.setSharedService(this);
    }
    
    /**
     * Broadcast an operation to all connected services except the sender
     * @param operation The operation to broadcast
     * @param senderClientId The client ID of the sender to exclude
     */
    public void broadcastOperation(TextOperation operation, String senderClientId) {
        // Broadcast to all other connected services
        System.out.println("Broadcasting operation: " + operation + " from client: " + senderClientId);
        for (MockCollaborationService2 service : connectedServices) {
            if (!service.getClientId().equals(senderClientId)) {
                System.err.println("Sending operation to client: " + service.getClientId());
                service.receiveRemoteOperation(operation);
            }
        }
    }
    
    /**
     * Get the current shared content
     * @return The current content
     */
    public String getInitialContent() {
        return sharedContent;
    }
}