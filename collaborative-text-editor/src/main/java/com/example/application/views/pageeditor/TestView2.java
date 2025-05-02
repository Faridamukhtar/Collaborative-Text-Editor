package com.example.application.views.pageeditor;

import com.example.application.views.pageeditor.socketsLogic.*;
import com.example.application.views.pageeditor.CRDT.*;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;

@Route("test-collab2")
public class TestView2 extends VerticalLayout {
    public TestView2() {
        setSizeFull();
        
        // Create a shared service with initial content
        SharedMockCollaborationService sharedService = new SharedMockCollaborationService("Start editing this shared document...");
        
        // Create two editor instances with unique client IDs
        MockCollaborationService2 service1 = new MockCollaborationService2("", "user1");
        MockCollaborationService2 service2 = new MockCollaborationService2("", "user2");
        
        // Register both services with the shared service
        sharedService.registerService(service1);
        sharedService.registerService(service2);
        
        // First editor instance
        H2 editor1Label = new H2("Editor 1 (User 1)");
        CollaborativeEditor editor1 = new CollaborativeEditor(service1);
        editor1.setHeight("40%");
        
        // Second editor instance
        H2 editor2Label = new H2("Editor 2 (User 2)");
        CollaborativeEditor editor2 = new CollaborativeEditor(service2);
        editor2.setHeight("40%");
        
        add(editor1Label, editor1, editor2Label, editor2);
    }
}