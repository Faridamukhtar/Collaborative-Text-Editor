package com.example.collaborative_editor.frontend;

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import java.util.UUID;

@Route("")
public class MainView extends AppLayout {

    public MainView() {
        H1 title = new H1("Collaborative Text Editor");
        
        VerticalLayout content = new VerticalLayout();
        content.setSizeFull();
        
        Button newDocumentButton = new Button("Create New Document", e -> {
            String documentId = UUID.randomUUID().toString();
            getUI().ifPresent(ui -> ui.navigate(EditorView.class, documentId));
        });
        
        content.add(title, newDocumentButton);
        content.setAlignItems(VerticalLayout.Alignment.CENTER);
        content.setJustifyContentMode(VerticalLayout.JustifyContentMode.CENTER);
        
        setContent(content);
    }
}