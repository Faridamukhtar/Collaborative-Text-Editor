package com.collab.backend;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;

    @Autowired
    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping
    public ResponseEntity<Document> createDocument(@RequestBody CreateDocumentRequest request) {
        Document document = documentService.createDocument(request.getTitle(), request.getUserId());
        return ResponseEntity.ok(document);
    }

    // Additional endpoints as needed...
}

class CreateDocumentRequest {
    private String title;
    private String userId;

    // Getters and setters
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
}