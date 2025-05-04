package com.example.application.views;

import com.vaadin.flow.component.html.Label;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@PageTitle("Editor")
@Route("page-editor/{userId}/{role}/{viewCode}/{editCode}")
public class TextView extends VerticalLayout {

    private String content = ""; // Default content if no value is set
    private String viewCode = "N/A"; // Default view code
    private String editCode = "N/A"; // Default edit code
    private String userId = "N/A"; // Default user ID
    private String role = "N/A"; // Default role



    public TextView(@PathVariable String userId, 
                    @PathVariable String role, 
                    @PathVariable String viewCode, 
                    @PathVariable String editCode) {

        // Set up basic layout
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        // Assign the path parameters to the class variables
        this.userId = userId;
        this.viewCode = viewCode;
        this.editCode = editCode;
        this.role = role;

        // Show codes
        add(new Label("User ID: " + userId));
        add(new Label("View Code: " + viewCode));
        add(new Label("Edit Code: " + editCode));
        add(new Label("Role: " + role));  // Display role

        // Show document content in a text area
        TextArea textArea = new TextArea("Document Content");
        textArea.setValue(content); // You could pass or retrieve content as needed
        textArea.setWidthFull();
        textArea.setHeight("400px");
        add(textArea);
    }
}
