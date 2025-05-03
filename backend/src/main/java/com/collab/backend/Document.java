package com.collab.backend;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Plain Document model (no database, no persistence)
 */
public class Document {

    private String documentId;
    private String title;
    private String content;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime lastModifiedAt;
    private Set<String> collaborators = new HashSet<>();
    private transient Map<String, Boolean> activeUsers;

    public Map<String, Boolean> getActiveUsers() {
        return activeUsers;
    }

    public void setActiveUsers(Map<String, Boolean> activeUsers) {
        this.activeUsers = activeUsers;
    }


    // Default constructor
    public Document() {
        this.documentId = UUID.randomUUID().toString();
        this.createdAt = LocalDateTime.now();
        this.lastModifiedAt = LocalDateTime.now();
    }

    // Constructor with title and creator
    public Document(String title, String createdBy) {
        this();
        this.title = title;
        this.createdBy = createdBy;
        this.collaborators.add(createdBy);
    }

    // Getters and setters
    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getLastModifiedAt() {
        return lastModifiedAt;
    }

    public void setLastModifiedAt(LocalDateTime lastModifiedAt) {
        this.lastModifiedAt = lastModifiedAt;
    }

    public Set<String> getCollaborators() {
        return collaborators;
    }

    public void setCollaborators(Set<String> collaborators) {
        this.collaborators = collaborators;
    }

    public void addCollaborator(String userId) {
        this.collaborators.add(userId);
    }

    public void removeCollaborator(String userId) {
        this.collaborators.remove(userId);
    }

    public void updateLastModified() {
        this.lastModifiedAt = LocalDateTime.now();
    }
}
