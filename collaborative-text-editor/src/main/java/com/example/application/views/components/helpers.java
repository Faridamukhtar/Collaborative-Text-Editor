package com.example.application.views.components;

public class helpers {
    public static String htmlToPlainText(String html) {
        if (html == null) return "";
        return html
                .replaceAll("(?i)<br */?>", "\n")
                .replaceAll("(?i)</p>", "\n")
                .replaceAll("<[^>]+>", "")
                .replaceAll("&nbsp;", " ")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("&amp;", "&")
                .trim();
    }
    public static String extractData(String result, String key) {
        String[] parts = result.split(", ");
        for (String part : parts) {
            if (part.startsWith(key + ":")) {
                return part.split(": ")[1];
            }
        }
        return "N/A";
    }
}