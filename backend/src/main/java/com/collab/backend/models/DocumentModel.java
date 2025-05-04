package com.collab.backend.models;

import com.collab.backend.CRDT.CrdtTree;

import java.util.HashMap;
import java.util.Map;

public class DocumentModel {
    private final String id;          // Unique ID for the document
    private final String viewCode;
    private final String editCode;

    private final CrdtTree crdtTree = new CrdtTree();

    // Map of userId -> UserModel
    private final Map<String, UserModel> users = new HashMap<>();

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
}
