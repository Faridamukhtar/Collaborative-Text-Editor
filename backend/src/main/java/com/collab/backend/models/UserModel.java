package com.collab.backend.models;

public class UserModel {
    private final String username;
    private final String role; 

    public UserModel(String username, String role) {
        this.username = username;
        this.role = role;
    }

    public String getUsername() {
        return username;
    }

    public String getRole() {
        return role;
    }
}
