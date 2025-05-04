package com.example.collaborative_editor.frontend;

import com.example.collaborative_editor.frontend.components.*;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.Route;
import java.util.UUID;

@Route("editor")
public class EditorView extends VerticalLayout implements HasUrlParameter<String> {
    
    private String documentId;
    private String userId;
    private String sessionId;
    private CollaborativeEditor editor;
    
    public EditorView() {
        setSizeFull();
        
        // Generate unique IDs for this session
        userId = "user-" + UUID.randomUUID().toString().substring(0, 8);
        sessionId = "session-" + UUID.randomUUID().toString().substring(0, 8);
    }
    
    @Override
    public void setParameter(BeforeEvent event, String parameter) {
        this.documentId = parameter;
        
        H2 title = new H2("Document: " + documentId);
        
        // Create collaborative editor
        editor = new CollaborativeEditor(documentId, userId, sessionId);
        editor.setSizeFull();
        
        // Add components to view
        add(title, editor);
    }
    
    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        editor.connect();
    }
    
    @Override
    protected void onDetach(DetachEvent detachEvent) {
        editor.disconnect();
        super.onDetach(detachEvent);
    }
}