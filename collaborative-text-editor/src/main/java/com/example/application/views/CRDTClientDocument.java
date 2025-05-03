package com.example.application.views;

import java.util.ArrayList;
import java.util.List;

public class CRDTClientDocument {
    private final List<String> operationsApplied = new ArrayList<>();
    private StringBuilder content = new StringBuilder();

    public void apply(Operation op) {
        switch (op.getType()) {
            case INSERT -> content.insert(op.getPosition(), op.getContent());
            case DELETE -> {
                int start = op.getPosition();
                int end = start + op.getContent().length();
                if (start >= 0 && end <= content.length()) {
                    content.delete(start, end);
                }
            }
            case UPDATE -> content = new StringBuilder(op.getContent());
        }
        operationsApplied.add(op.getUserId() + ":" + op.getTimestamp());
    }

    public String getContent() {
        return content.toString();
    }

    public void setInitialContent(String rawHtml) {
        this.content = new StringBuilder(rawHtml);
    }
}