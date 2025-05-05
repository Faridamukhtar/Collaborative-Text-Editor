package com.collab.backend.service;

import com.collab.backend.models.DocumentModel;
import com.collab.backend.models.UserModel;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class DocumentService {

    // Access code (view/edit) -> DocumentModel
    private final Map<String, DocumentModel> documents = new ConcurrentHashMap<>();

    // documentId -> DocumentModel
    private final Map<String, DocumentModel> documentsById = new ConcurrentHashMap<>();

    // Auto-incrementing user ID generator
    private final AtomicInteger userIdCounter = new AtomicInteger(1);

    /**
     * Create a new document and assign view/edit codes.
     * @param initialContent optional content to initialize the CRDT
     * @return Map containing documentId, viewCode, editCode
     */
    public Map<String, String> createDocument(String initialContent) {
        String documentId = generateDocumentId();
        String viewCode = generateCode();
        String editCode = generateCode();

        DocumentModel doc = new DocumentModel(documentId, viewCode, editCode);
        if (initialContent != null && !initialContent.isEmpty()) {
            doc.setContent(initialContent);
        }

        // Store document
        documents.put(viewCode, doc);
        documents.put(editCode, doc);
        documentsById.put(documentId, doc);

        return Map.of(
                "documentId", documentId,
                "viewCode", viewCode,
                "editCode", editCode
        );
    }

    /**
     * Join a document using either viewCode or editCode.
     * Generates a userId and assigns editor/viewer role.
     * @param code the view or edit code
     * @return Map containing userId, role, documentId, viewCode
     */
    public Map<String, String> joinDocument(String code) {
        DocumentModel doc = documents.get(code);
        if (doc == null) {
            throw new IllegalArgumentException("Invalid document code: " + code);
        }

        String userId = "user-" + userIdCounter.getAndIncrement();
        String role = code.equals(doc.getEditCode()) ? "editor" : "viewer";

        UserModel user = new UserModel(userId, role);
        doc.addUser(userId, user);

        // Set editor/viewer ID fields
        if (role.equals("editor")) {
            doc.setEditorId(userId);
        } else {
            doc.setViewerId(userId);
        }

        return Map.of(
                "documentId", doc.getId(),
                "role", role,
                "userId", userId,
                "viewCode", doc.getViewCode()
        );
    }

    /**
     * Retrieve a document by its internal ID.
     * @param documentId the document's UUID
     * @return DocumentModel instance or null
     */
    public DocumentModel getDocumentById(String documentId) {
        return documentsById.get(documentId);
    }

    /**
     * Helper to generate random short access codes.
     */
    private String generateCode() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Helper to generate unique document ID.
     */
    private String generateDocumentId() {
        return "doc-" + UUID.randomUUID().toString();
    }
}