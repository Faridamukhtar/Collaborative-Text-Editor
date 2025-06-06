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

    private final Map<String, DocumentModel> documents = new ConcurrentHashMap<>();

    private final Map<String, DocumentModel> documentsById = new ConcurrentHashMap<>();

    private final AtomicInteger userIdCounter = new AtomicInteger(1);

    public Map<String, String> createDocument(String initialContent) {
        String documentId = generateDocumentId();
        String viewCode = generateCode();
        String editCode = generateCode();

        DocumentModel doc = new DocumentModel(documentId, viewCode, editCode);
        if (initialContent != null && !initialContent.isEmpty()) {
            doc.setContent(initialContent);
        }

        documents.put(viewCode, doc);
        documents.put(editCode, doc);
        documentsById.put(documentId, doc);

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