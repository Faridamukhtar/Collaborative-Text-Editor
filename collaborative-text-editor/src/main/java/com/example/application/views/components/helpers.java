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

}
