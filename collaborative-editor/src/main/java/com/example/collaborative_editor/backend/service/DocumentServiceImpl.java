package com.example.collaborative_editor.backend.service;

import com.example.collaborative_editor.backend.crdt.CrdtDocument;
import com.example.collaborative_editor.model.Document;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DocumentServiceImpl implements DocumentService {
    private final Map<String, Document> documents = new ConcurrentHashMap<>();
    private final Map<String, CrdtDocument> crdtDocuments = new ConcurrentHashMap<>();
    
    @Override
    public Document createDocument(String title, String userId) {
        Document document = new Document(title, userId);
        documents.put(document.getId(), document);
        crdtDocuments.put(document.getId(), new CrdtDocument("server"));
        return document;
    }
    
    @Override
    public Optional<Document> getDocumentById(String id) {
        return Optional.ofNullable(documents.get(id));
    }
    
    @Override
    public List<Document> getAllDocuments() {
        return new ArrayList<>(documents.values());
    }
    
    @Override
    public CrdtDocument getCrdtDocumentById(String id) {
        return crdtDocuments.computeIfAbsent(id, docId -> new CrdtDocument("server"));
    }
    
    @Override
    public void saveCrdtDocument(String id, CrdtDocument document) {
        crdtDocuments.put(id, document);
        
        // Update document metadata
        Document doc = documents.get(id);
        if (doc != null) {
            doc.setUpdatedAt(LocalDateTime.now());
        }
    }
    
    @Override
    public void deleteDocument(String id) {
        documents.remove(id);
        crdtDocuments.remove(id);
    }
}