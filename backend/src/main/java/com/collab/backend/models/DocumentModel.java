package com.collab.backend.models;

import com.collab.backend.crdt.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DocumentModel {
    private final String id;          // Unique ID for the document
    private final String viewCode;
    private final String editCode;

    private String editorId;
    private String viewerId;

    private final CrdtTree crdtTree = new CrdtTree();

    // userId -> UserModel
    private final Map<String, UserModel> users = new HashMap<>();

    // userId -> cursor position as string (e.g., "12" or "line:5,col:3")
    private final Map<String, String> userCursors = new HashMap<>();

    // Active WebSocket user IDs
    private final Set<String> activeUsers = new HashSet<>();

    private final List<CommentModel> comments = new ArrayList<>();


    public DocumentModel(String id, String viewCode, String editCode) {
        this.id = id;
        this.viewCode = viewCode;
        this.editCode = editCode;
    }

    public String getId() {
        return id;
    }

    public String getViewCode() {
        return viewCode;
    }

    public String getEditCode() {
        return editCode;
    }

    public String getEditorId() {
        return editorId;
    }

    public String getViewerId() {
        return viewerId;
    }

    public void setEditorId(String editorId) {
        this.editorId = editorId;
    }

    public void setViewerId(String viewerId) {
        this.viewerId = viewerId;
    }

    public Map<String, UserModel> getUsers() {
        return users;
    }

    public void addUser(String userId, UserModel user) {
        users.put(userId, user);
    }

    public CrdtTree getCrdtTree() {
        return crdtTree;
    }

    public String getContent() {
        return crdtTree.getText();
    }

    public void setContent(String newContent) {
        crdtTree.clear();
        for (int i = 0; i < newContent.length(); i++) {
            crdtTree.insert(String.valueOf(newContent.charAt(i)), i, "initUser", System.currentTimeMillis() + i);
        }
    }

    public Set<String> getActiveUsers() {
        return activeUsers;
    }

    public void addActiveUser(String userId) {
        activeUsers.add(userId);
    }

    public void removeActiveUser(String userId) {
        activeUsers.remove(userId);
    }

    public Map<String, String> getUserCursors() {
        return userCursors;
    }

    public void updateUserCursor(String userId, String cursorPosition) {
        userCursors.put(userId, cursorPosition);
    }

    public List<CommentModel> getComments() {
        return comments;
    }
    
    public void addComment(CommentModel comment) {
        comments.add(comment);
    }
    
    public void removeCommentById(String commentId) {
        comments.removeIf(c -> c.getCommentId().equals(commentId));
    }
    
}