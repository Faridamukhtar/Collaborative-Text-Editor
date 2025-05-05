package com.collab.backend.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.collab.backend.service.DocumentService;

@RestController
public class DocumentRestController {

    @Autowired
    private DocumentService documentService;



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
                ", documentId: " + response.get("documentId") +
               ", viewCode: " + codes.get("viewCode") +
               ", editCode: " + codes.get("editCode");
    }

    @GetMapping("/join/{documentID}")
    public String joinDocument(@PathVariable String documentID) {
        // The username is required for joining the document
        var response = documentService.joinDocument(documentID);
        return "userId: " + response.get("userId") +
                ", documentId: " + response.get("documentId") +
                ", role: " + response.get("role") +
                ", viewCode: " + response.get("viewCode");
    }
}