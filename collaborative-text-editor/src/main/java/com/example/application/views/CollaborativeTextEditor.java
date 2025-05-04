package com.example.application.views;

import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.DescriptionList;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.Command;
import com.vaadin.flow.server.StreamResource;
import com.vaadin.flow.server.VaadinSession;


import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import com.example.application.views.components.SidebarUtil;
import com.example.application.views.components.helpers;
import com.vaadin.flow.component.html.Section;
import com.vaadin.flow.component.icon.VaadinIcon;



@Route("/editor")
@JsModule("./js/text-editor-connector.js")
public class CollaborativeTextEditor extends VerticalLayout {
    private final UI ui;
    private final TextArea editor;
    private final String userId;
    private final String viewCode;
    private final String editCode;
    private Anchor hiddenDownloadLink;

    // Store for active users
    private static final ConcurrentHashMap<String, UI> activeUsers = new ConcurrentHashMap<>();

    public CollaborativeTextEditor() {
        this.ui = UI.getCurrent();
        String content = (String) VaadinSession.getCurrent().getAttribute("importedText");
        this.viewCode = (String) VaadinSession.getCurrent().getAttribute("viewCode");
        this.editCode = (String) VaadinSession.getCurrent().getAttribute("editCode");
        this.userId = (String) VaadinSession.getCurrent().getAttribute("userId");
    
        // Register this user
        activeUsers.put(userId, ui);
    
        // Clean up on UI detach
        ui.addDetachListener(event -> activeUsers.remove(userId));
    
        // Set up the layout
        setSizeFull();
        setPadding(true);
        setSpacing(true);
    
        // Header
        H2 title = new H2("Live Collaborative Editor");
        title.addClassName("header");
    
        // Create a Div for the header and set padding
        Div header = new Div(title);
        header.getStyle().set("text-align", "left");
    
        // Editor
        editor = new TextArea();
        if(content != null)
            editor.setValue(content);
        editor.setWidthFull();
        editor.setHeight("100%");
        editor.setLabel("Edit text below - changes are shared with all users");
    
        editor.addValueChangeListener(event -> {
            String updatedHtml = event.getValue();
            updateExportResource(updatedHtml);
        });
    
        VerticalLayout editorLayout = new VerticalLayout(header, editor);
        editorLayout.setSizeFull();
        editorLayout.setPadding(false);
        editorLayout.setSpacing(false);
        editorLayout.setFlexGrow(1, editor);
    
       hiddenDownloadLink = new Anchor();
        hiddenDownloadLink.setId("hiddenDownloadLink");
        hiddenDownloadLink.getStyle().set("display", "none");
        hiddenDownloadLink.getElement().setAttribute("download", true);
    
        Button exportButton = new Button("Export", VaadinIcon.DOWNLOAD.create());
        exportButton.setWidthFull();
        exportButton.addClickListener(e -> UI.getCurrent().getPage().executeJs("document.getElementById('hiddenDownloadLink').click();"));
    
        Button resetButton = new Button("Reset Editor", e -> {
            editor.setValue("");
            broadcastToAll("reset", "", 0);
            Notification.show("Editor content reset by " + userId);
        });
        resetButton.setWidthFull();
    
        Div connectionStatus = new Div();
        connectionStatus.setText("Connected Users: " + activeUsers.size());
        connectionStatus.getStyle().set("color", "black");
    
        VerticalLayout sidebarLayout = new VerticalLayout();
        sidebarLayout.setWidth("300px");
        sidebarLayout.getStyle()
            .set("background-color", "#f5f5f5")
            .set("padding", "1rem");
        sidebarLayout.addClassNames("sidebar");
        sidebarLayout.add(
            SidebarUtil.createItem("Owner", userId),
            SidebarUtil.createBadgeItem("View Code", viewCode),
            SidebarUtil.createBadgeItem("Edit Code", editCode),
            hiddenDownloadLink,
            exportButton,
            resetButton,
            connectionStatus
        );
    
        HorizontalLayout mainLayout = new HorizontalLayout(editorLayout, sidebarLayout);
        mainLayout.setSizeFull();
        mainLayout.setFlexGrow(3, editorLayout);   // Give editor 75%
        mainLayout.setFlexGrow(1, sidebarLayout);  // Sidebar 25%
    
        add(mainLayout);
    
        initializeConnector(); 
    }
    
    private void initializeConnector() {
        getElement().executeJs("window.initEditorConnector($0, $1)", getElement(), userId);
    }

    @ClientCallable
    public void onCharacterInserted(String character, int position) {
        System.out.println("[Insert] User " + userId + " inserted '" + character + "' at pos: " + position);
        broadcastToAll("insert", character, position);
    }

    @ClientCallable
    public void onCharacterDeleted(int position) {
        System.out.println("[Delete] User " + userId + " deleted at pos: " + position);
        broadcastToAll("delete", "", position);
    }

    @ClientCallable
    public void onCharacterBatchInserted(String text, int position) {
        System.out.println("[Batch Insert] '" + text + "' at pos: " + position);
        for (int i = 0; i < text.length(); i++) {
            String ch = String.valueOf(text.charAt(i));
            broadcastToAll("insert", ch, position + i);
        }
    }

    @ClientCallable
    public void onCharacterBatchDeleted(int startPosition, int count) {
        System.out.println("[Batch Delete] " + count + " chars from pos: " + startPosition);
        for (int i = 0; i < count; i++) {
            broadcastToAll("delete", "", startPosition);
        }
    }

    private void broadcastToAll(String operation, String character, int position) {
        activeUsers.forEach((id, userUI) -> {
            if (!id.equals(userId)) {
                userUI.access((Command) () -> {
                    userUI.getPage().executeJs(
                        "window.applyRemoteChange($0, $1, $2, $3)",
                        operation, character, position, userId
                    );
                });
            }
        });
    }

    private void updateExportResource(String htmlContent) {
        String plainText = helpers.htmlToPlainText(htmlContent);
        StreamResource resource = new StreamResource("document.txt",
                () -> new ByteArrayInputStream(plainText.getBytes(StandardCharsets.UTF_8)));
        hiddenDownloadLink.setHref(resource);
    }

} 
