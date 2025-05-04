package com.example.application.views;

import com.example.application.connections.CRDT.*;
import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.Route;
import org.springframework.beans.factory.annotation.Autowired;

import com.vaadin.flow.server.StreamResource;
import com.vaadin.flow.server.VaadinSession;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import com.example.application.views.components.SidebarUtil;
import com.example.application.views.components.helpers;
import com.vaadin.flow.component.icon.VaadinIcon;


@Route("/editor")
@JsModule("./js/text-editor-connector.js")
public class CollaborativeTextEditor extends VerticalLayout implements CollaborativeEditUiListener {
    private final UI ui;
    private final TextArea editor;
    private final String userId;
    private boolean suppressInput = false;
    private final String viewCode;
    private final String editCode;
    private Anchor hiddenDownloadLink;
    private static final ConcurrentHashMap<String, UI> activeUsers = new ConcurrentHashMap<>();

    @Autowired
    private CollaborativeEditService collaborativeEditService;

    public CollaborativeTextEditor() {
        this.ui = UI.getCurrent();

        String content = (String) VaadinSession.getCurrent().getAttribute("importedText");
        String vc = (String) VaadinSession.getCurrent().getAttribute("viewCode");
        String ec = (String) VaadinSession.getCurrent().getAttribute("editCode");
        this.userId = (String) VaadinSession.getCurrent().getAttribute("userId");
        this.viewCode = (vc == null || vc.isEmpty()) ? "N/A" : vc;
        this.editCode = (ec == null || ec.isEmpty()) ? "N/A" : ec;

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

        if (!viewCode.isEmpty() && editCode.isEmpty()) {
            editor.setReadOnly(true); 
        }  
    
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

        CollaborativeEditService.setUiListener(this);
          
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
        if (suppressInput) return;
        ClientEditRequest req = CollaborativeEditService.createInsertRequest(character, position, userId);
        collaborativeEditService.sendEditRequest(req);
    }

    @ClientCallable
    public void onCharacterDeleted(int position) {
        if (suppressInput) return;
        ClientEditRequest req = CollaborativeEditService.createDeleteRequest(position, userId);
        collaborativeEditService.sendEditRequest(req);
    }

    @ClientCallable
    public void onCharacterBatchInserted(String text, int position) {
        if (suppressInput) return;
        for (int i = 0; i < text.length(); i++) {
            String ch = String.valueOf(text.charAt(i));
            onCharacterInserted(ch, position + i);
        }
    }

    @ClientCallable
    public void onCharacterBatchDeleted(int startPosition, int count) {
        if (suppressInput) return;
        for (int i = 0; i < count; i++) {
            onCharacterDeleted(startPosition);
        }
    }

    @Override
    public void onServerMessage(String text) {
        System.out.println("userId: " + userId);
        System.out.println("📩 Message received from server in UI: " + text);
        suppressInput = true;
        ui.access(() -> {
            editor.setValue(text); // Won't trigger backend logic if suppression is on
            suppressInput = false; // Reset after value is set
        });
    }

    private void updateExportResource(String htmlContent) {
        String plainText = helpers.htmlToPlainText(htmlContent);
        StreamResource resource = new StreamResource("document.txt",
                () -> new ByteArrayInputStream(plainText.getBytes(StandardCharsets.UTF_8)));
        hiddenDownloadLink.setHref(resource);
    }

} 