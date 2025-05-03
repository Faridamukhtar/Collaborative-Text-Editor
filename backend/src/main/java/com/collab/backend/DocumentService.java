package com.collab.backend;

import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Stateless DocumentService with in-memory volatile storage only.
 */
@Service
public class DocumentService {

    // Runtime-only document map (cleared when server restarts)
    private final Map<String, Document> documentMap = new HashMap<>();

    public Document getDocumentById(String documentId) {
        Document doc = documentMap.get(documentId);
        if (doc == null) {
            throw new RuntimeException("Document not found: " + documentId);
        }
        return doc;
    }

    public List<Document> getDocumentsByUserId(String userId) {
        List<Document> results = new ArrayList<>();
        for (Document doc : documentMap.values()) {
            if (doc.getCollaborators().contains(userId)) {
                results.add(doc);
            }
        }
        return results;
    }

    public Document createDocument(String title, String userId) {
        Document doc = new Document(title, userId);
        documentMap.put(doc.getDocumentId(), doc);
        return doc;
    }

    public Document updateDocument(String documentId, String title, String content) {
        Document doc = getDocumentById(documentId);
        if (title != null && !title.isBlank()) doc.setTitle(title);
        if (content != null) doc.setContent(content);
        doc.updateLastModified();
        return doc;
    }

    public void deleteDocument(String documentId) {
        documentMap.remove(documentId);
    }

    public Document addCollaborator(String documentId, String userId) {
        Document doc = getDocumentById(documentId);
        doc.addCollaborator(userId);
        doc.updateLastModified();
        return doc;
    }

    public Document removeCollaborator(String documentId, String userId) {
        Document doc = getDocumentById(documentId);
        doc.removeCollaborator(userId);
        doc.updateLastModified();
        return doc;
    }

    public void loadDocumentToCrdt(String documentId, TreeCrdt crdt) {
        Document doc = getDocumentById(documentId);
        if (doc.getContent() != null && !doc.getContent().isEmpty()) {
            crdt.applyOperation(new Operation.Builder()
                    .type(Operation.Type.INSERT)
                    .parentId(crdt.getRoot().getId())
                    .content(doc.getContent())
                    .userId(doc.getCreatedBy())
                    .position(0)
                    .build());
        }
    }

    public Document saveDocumentFromCrdt(String documentId, TreeCrdt crdt) {
        return updateDocument(documentId, null, crdt.getDocumentContent());
    }

    /**
     * Returns the live document object with content from CRDT and active user map.
     */
    public Document getLiveDocument(String documentId, TreeCrdt crdt) {
        Document doc = getDocumentById(documentId);
        doc.setContent(crdt.getDocumentContent());

        // Optionally populate active user flags (all false by default here)
        Map<String, Boolean> activeUsers = new HashMap<>();
        for (String userId : doc.getCollaborators()) {
            activeUsers.put(userId, false);
        }
        doc.setActiveUsers(activeUsers);

        return doc;
    }
}
