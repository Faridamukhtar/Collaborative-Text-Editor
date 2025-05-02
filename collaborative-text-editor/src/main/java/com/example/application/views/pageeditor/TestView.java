package com.example.application.views.pageeditor;

import com.example.application.views.pageeditor.socketsLogic.*;
import com.example.application.views.pageeditor.CRDT.*;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;

@Route("test-collab")
public class TestView extends VerticalLayout {
    public TestView() {
        setSizeFull();
        
        // Create two editor instances with unique client IDs
        MockCollaborationService service1 = new MockCollaborationService("Initial content for user1");

        CollaborativeEditor editor1 = new CollaborativeEditor(service1);
        // Add the editors to the view
        add(editor1);
    }
}
