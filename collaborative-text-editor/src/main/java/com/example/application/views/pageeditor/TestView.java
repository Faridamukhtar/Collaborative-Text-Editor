package com.example.application.views;

import com.example.application.views.pageeditor.socketsLogic.*;
import com.example.application.views.pageeditor.CRDT.*;
import com.example.application.views.pageeditor.*;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;

@Route("test-collab")
public class TestView extends VerticalLayout {
    public TestView() {
        setSizeFull();
        
        // Create two editor instances sharing the same mock service
        MockCollaborationService service1 = new MockCollaborationService("Start typing here...");
        MockCollaborationService service2 = new MockCollaborationService("Start typing here...");
        
        // First editor instance
        CollaborativeEditor editor1 = new CollaborativeEditor(service1);
        editor1.setHeight("50%");
        
        // Second editor instance
        CollaborativeEditor editor2 = new CollaborativeEditor(service2);
        editor2.setHeight("50%");
        
        // Cross-wire the services to simulate two clients
        service1.subscribeToChanges(service2::sendOperation);
        service2.subscribeToChanges(service1::sendOperation);
        
        add(editor1, editor2);
    }
}