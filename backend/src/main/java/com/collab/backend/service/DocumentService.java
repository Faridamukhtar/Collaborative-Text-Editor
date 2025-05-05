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
    private final AtomicInteger userIdCounter = new AtomicInteger(1);

    public Map<String, String> createDocument(String initialContent) {
        String viewCode = generateCode();
        String editCode = generateCode();

        DocumentModel doc = new DocumentModel(initialContent, viewCode, editCode);
        documents.put(viewCode, doc);
        documents.put(editCode, doc);

        return Map.of("viewCode", viewCode, "editCode", editCode);
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

        return Map.of("role", role, "userId", userId);
    }

    private String generateCode() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
