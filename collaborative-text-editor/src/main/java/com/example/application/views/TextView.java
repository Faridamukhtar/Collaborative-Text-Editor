package com.example.application.views;

import com.vaadin.flow.component.html.Label;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;

@PageTitle("Editor")
@Route("/page-editor")
public class TextView extends VerticalLayout {

    public TextView() {
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        String content = (String) VaadinSession.getCurrent().getAttribute("importedText");
        String viewCode = (String) VaadinSession.getCurrent().getAttribute("viewCode");
        String editCode = (String) VaadinSession.getCurrent().getAttribute("editCode");
        String userId = (String) VaadinSession.getCurrent().getAttribute("userId");
        String role = (String) VaadinSession.getCurrent().getAttribute("role");


        if (content == null) content = "";
        if (viewCode == null) viewCode = "N/A";
        if (editCode == null) editCode = "N/A";
        if (userId == null) userId = "N/A";

        // Show codes
        add(new Label("User ID: " + userId));
        add(new Label("View Code: " + viewCode));
        add(new Label("Edit Code: " + editCode));

        // Show document content
        TextArea textArea = new TextArea("Document Content");
        textArea.setValue(content);
        textArea.setWidthFull();
        textArea.setHeight("400px");
        add(textArea);
    }
}
