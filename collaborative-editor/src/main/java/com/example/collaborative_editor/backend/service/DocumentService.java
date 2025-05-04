package com.example.collaborative_editor.backend.service;

import com.example.collaborative_editor.backend.crdt.CrdtDocument;
import com.example.collaborative_editor.model.Document;

import java.util.List;
import java.util.Optional;

public interface DocumentService {
    /**
     * Create a new document
     */
    Document createDocument(String title, String userId);
    
    /**
     * Get document by ID
     */
    Optional<Document> getDocumentById(String id);
    
    /**
     * Get all documents
     */
    List<Document> getAllDocuments();
    
    /**
     * Get CRDT document by ID
     */
    CrdtDocument getCrdtDocumentById(String id);
    
    /**
     * Save CRDT document state
     */
    void saveCrdtDocument(String id, CrdtDocument document);
    
    /**
     * Delete document
     */
    void deleteDocument(String id);
}