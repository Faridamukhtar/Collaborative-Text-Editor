package com.example.application.views;


import com.example.application.views.pageeditor.socketsLogic.*;
import com.example.application.views.pageeditor.*;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;

@Route("collaborative-editor")
public class CollaborativeEditorPage extends VerticalLayout {
    
    public CollaborativeEditorPage() {
        setSizeFull();

        // ======= Configuration for real server connection =======
        String serverUrl = "ws://localhost:8081";
        String documentId = "doc-001";
        String userId = "user-xyz";
        // ========================================================

        // Initialize collaboration service with WebSocket
        CollaborationService collaborationService = new WebSocketCollaborationService(
            serverUrl,
            documentId,
            userId
        );

        // Create and add the collaborative editor to the view
        CollaborativeEditor editor = new CollaborativeEditor(collaborationService);
        H2 header = new H2("Connected to: " + documentId);

        add(header, editor);
    }
}
