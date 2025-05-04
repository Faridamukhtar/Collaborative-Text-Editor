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

import com.example.application.views.components.helpers;


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
                VaadinSession.getCurrent().setAttribute("importedText", "");
                getUI().ifPresent(ui -> ui.navigate(CollaborativeTextEditor.class, result));

                getUI().ifPresent(ui -> ui.navigate("/editor"));

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
                    VaadinSession.getCurrent().setAttribute("importedText", content);
                    getUI().ifPresent(ui -> ui.navigate(CollaborativeTextEditor.class, result));
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
                if (response.startsWith("userId:")) {
                    String userId = helpers.extractData(response, "userId");
                    String role = helpers.extractData(response, "role");
                    String viewCode = "Hidden", editCode = "Hidden";
                    if ("viewer".equals(role))
                        viewCode = code;
                    else
                        editCode = code;
                    String params = "userId: " + userId +
                            ", role: " + role + 
                        ", viewCode: " + viewCode +
                        ", editCode: " + editCode;

                    getUI().ifPresent(ui -> ui.navigate(CollaborativeTextEditor.class, params));
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


}
