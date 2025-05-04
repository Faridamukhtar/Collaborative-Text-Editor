package com.collab.backend.service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.collab.backend.models.DocumentModel;
import com.collab.backend.models.UserModel;
import org.springframework.stereotype.Service;

@Service
public class DocumentService {
    private final Map<String, DocumentModel> documents = new ConcurrentHashMap<>();
    private final Map<String, DocumentModel> documentsById = new ConcurrentHashMap<>(); // New map for document IDs
    private final AtomicInteger userIdCounter = new AtomicInteger(1);

    public Map<String, String> createDocument(String initialContent) {
        String documentId = generateDocumentId();
        String viewCode = generateCode();
        String editCode = generateCode();

        DocumentModel doc = new DocumentModel(documentId, viewCode, editCode);
        documents.put(viewCode, doc);
        documents.put(editCode, doc);
        documentsById.put(documentId, doc); // Store by document ID

        return Map.of(
            "documentId", documentId,
            "viewCode", viewCode,
            "editCode", editCode
        );
    }

    public Map<String, String> joinDocument(String code) {
        DocumentModel doc = documents.get(code);
        if (doc == null) {
            throw new IllegalArgumentException("Invalid document code: " + code);
        }

        String userId = "user-" + userIdCounter.getAndIncrement();
        String role = code.equals(doc.getEditCode()) ? "editor" : "viewer";

        UserModel user = new UserModel(userId, role);
        doc.addUser(userId, user);

        return Map.of(
            "documentId", doc.getId(),
            "role", role,
            "userId", userId
        );
    }

    // Get document by ID
    public DocumentModel getDocumentById(String documentId) {
        return documentsById.get(documentId);
    }

    private String generateCode() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private String generateDocumentId() {
        return "doc-" + UUID.randomUUID().toString();
    }
}