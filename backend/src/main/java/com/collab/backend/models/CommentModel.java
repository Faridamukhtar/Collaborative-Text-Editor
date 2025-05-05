package com.collab.backend.models;

public class CommentModel {
    private String commentId;
    private String userId;
    private String text;
    private int startIndex;
    private int endIndex;

    public CommentModel(String userId, String commentId, String text, int startIndex, int endIndex) {
        this.userId = userId;
        this.commentId = commentId;
        this.text = text;
        this.startIndex = startIndex;
        this.endIndex = endIndex;
    }

    // Getters and setters
    public String getCommentId() { return commentId; }
    public String getText() { return text; }
    public int getStartIndex() { return startIndex; }
    public int getEndIndex() { return endIndex; }
    public String getUserId() { return userId; }

    public void setText(String text) { this.text = text; }
    public void setStartIndex(int startIndex) { this.startIndex = startIndex; }
    public void setEndIndex(int endIndex) { this.endIndex = endIndex; }
}
