package com.example.application.views;

import com.example.application.data.StartPageData;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@PageTitle("Start Screen")
@Route("")
public class StartView extends VerticalLayout {

    public StartView() {
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        // === New Document Section ===
        VerticalLayout newDocLayout = new VerticalLayout();
        newDocLayout.setAlignItems(Alignment.CENTER);
        Image newDocIcon = new Image("icons/new-file.png", "New Doc");
        newDocIcon.setWidth("60px");
        Button newDocBtn = new Button("New Doc.");
        newDocBtn.addClickListener(event -> {
            String result = StartPageData.createNewDocument(""); 
            if (result.startsWith("userId:")) {
                String userId = extractData(result, "userId");
                String role = extractData(result, "role");
                String viewCode = extractData(result, "viewCode");
                String editCode = extractData(result, "editCode");
                String navigateTo = String.format("page-editor/%s/%s/%s/%s", 
                                                    userId, role, viewCode, editCode);
                getUI().ifPresent(ui -> ui.navigate(navigateTo));
            } else {
                Notification.show("Failed to create document: " + result, 5000, Notification.Position.MIDDLE);
            }
        });
        newDocLayout.add(newDocIcon, newDocBtn);

        // === Import Document Section ===
        VerticalLayout importLayout = new VerticalLayout();
        importLayout.setAlignItems(Alignment.CENTER);
        Image importIcon = new Image("icons/file-import.png", "Import");
        importIcon.setWidth("60px");
        MemoryBuffer buffer = new MemoryBuffer();
        Upload upload = new Upload(buffer);
        upload.addClassName("custom-upload");
        upload.setUploadButton(new Button("Import files"));
        upload.setAcceptedFileTypes(".txt");
        importLayout.setSpacing(true);
        importLayout.add(importIcon, upload);

        upload.addSucceededListener(event -> {
            try (InputStream inputStream = buffer.getInputStream()) {
                String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        
                String result = StartPageData.createNewDocument(content);
        
                if (result.startsWith("userId:")) {
                    String userId = extractData(result, "userId");
                    String role = extractData(result, "role");
                    String viewCode = extractData(result, "viewCode");
                    String editCode = extractData(result, "editCode");
        
                    String navigateTo = String.format("page-editor/%s/%s/%s/%s", userId, role, viewCode, editCode);        
                    VaadinSession.getCurrent().setAttribute("importedText", content);
                    getUI().ifPresent(ui -> ui.navigate(navigateTo));
                } else {
                    Notification.show("Failed to create document: " + result, 5000, Notification.Position.MIDDLE);
                }
            } catch (IOException e) {
                Notification.show("Error reading file: " + e.getMessage(), 5000, Notification.Position.MIDDLE);
            }
        });
        

        // === Join Session Section ===
        VerticalLayout joinLayout = new VerticalLayout();
        joinLayout.setAlignItems(Alignment.CENTER);
        Image joinIcon = new Image("icons/join.png", "Join");
        joinIcon.setWidth("60px");

        HorizontalLayout joinRow = new HorizontalLayout();
        TextField sessionCode = new TextField();
        sessionCode.setPlaceholder("Session Code");
        Button joinBtn = new Button("Join");
        joinBtn.addClickListener(event -> {
            String code = sessionCode.getValue().trim();
            if (!code.isEmpty()) {
                String response = StartPageData.joinDocument(code, "guestUser");
                if (response.startsWith("role:")) {
                    String userId = extractData(response, "userId");
                    String role = extractData(response, "role");

                    // Store the session code in the Vaadin session

                    // Construct the URL with the extracted data
                    String viewCode ="" , editCode="";
                    if (role == "viewer")
                        viewCode = code;
                    else 
                        editCode = code;
                        
                        VaadinSession.getCurrent().setAttribute("importedText", "");
                        
                        String navigateTo = String.format("page-editor/%s/%s/%s/%s", userId, role, viewCode, editCode);
                        getUI().ifPresent(ui -> ui.navigate(navigateTo));
                } else {
                    Notification.show("Join failed: " + response, 5000, Notification.Position.MIDDLE);
                }
            } else {
                Notification.show("Please enter a session code");
            }
        });
        joinRow.add(sessionCode, joinBtn);
        joinRow.setAlignItems(Alignment.BASELINE);
        joinLayout.add(joinIcon, joinRow);

        // Combine all three sections
        HorizontalLayout mainLayout = new HorizontalLayout(newDocLayout, importLayout, joinLayout);
        mainLayout.setAlignItems(Alignment.CENTER);
        mainLayout.setJustifyContentMode(JustifyContentMode.CENTER);
        add(mainLayout);
    }
    private String extractData(String result, String key) {
        // Assuming the result is in the format: "key: value"
        String[] parts = result.split(", ");
        for (String part : parts) {
            if (part.startsWith(key + ":")) {
                return part.split(": ")[1];
            }
        }
        return "";
    }
}
