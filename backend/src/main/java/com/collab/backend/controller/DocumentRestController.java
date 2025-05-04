package com.collab.backend.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import com.collab.backend.service.DocumentService;

@RestController
public class DocumentRestController {

    @Autowired
    private DocumentService documentService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    
    private final Map<String, List<String>> documentUsers = new ConcurrentHashMap<>();

    @PostMapping("/create")
    public String createNewDocument(@RequestBody(required = false) String initialContent) {
        System.out.println("Creating new document with initial content: " + initialContent);
        if (initialContent == null) {
            initialContent = "";
        }

        // Create a new document and get the view and edit codes
        var codes = documentService.createDocument(initialContent);
        
        // Join the document as an editor (using the edit code)
        var response = documentService.joinDocument(codes.get("editCode"));

        return "userId: " + response.get("userId") +
                ", role: " + response.get("role") + 
                ", viewCode: " + codes.get("viewCode") +
                ", editCode: " + codes.get("editCode");
    }

    @GetMapping("/join/{documentID}")
    public String joinDocument(@PathVariable String documentID) {
        // The username is required for joining the document       
        var response = documentService.joinDocument(documentID);
        String userId = response.get("userId");
        String role = response.get("role");

        // Track user in active users list
        documentUsers.computeIfAbsent(documentID, _ -> new ArrayList<>());
        if (!documentUsers.get(documentID).contains(userId)) {
            documentUsers.get(documentID).add(userId);
        }

        // Broadcast updated list to everyone in this document
        messagingTemplate.convertAndSend(
            "/topic/users/" + documentID,
            documentUsers.get(documentID)
        );

        return "role:  " + role + ", userId: " + userId;
    }
}
