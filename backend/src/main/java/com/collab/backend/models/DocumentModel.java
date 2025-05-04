package com.collab.backend.models;

import java.util.HashMap;
import java.util.Map;

public class DocumentModel {
        private String content;
    private final String viewCode;
    private final String editCode;

    // Map of userId -> UserModel
    private final Map<String, UserModel> users = new HashMap<>();

    public DocumentModel(String content, String viewCode, String editCode) {
        this.content = content;
        this.viewCode = viewCode;
        this.editCode = editCode;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
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
}
