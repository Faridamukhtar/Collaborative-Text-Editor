package com.example.application.data;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class StartPageData {

    private static final String BASE_URL = "http://localhost:8081";
    private static final HttpClient client = HttpClient.newHttpClient();

    public static String createNewDocument(String initialString) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(BASE_URL + "/create"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(initialString != null ? initialString : ""))
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return response.body();
            } else {
                return "Error: " + response.statusCode() + " - " + response.body();
            }
        } catch (Exception e) {
            return "Unexpected error: " + e.getMessage();
        }
    }

    public static String joinDocument(String code, String username) {
        try {
            String url = BASE_URL + "/join/" + code + "?username=" + username;
            HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(url))
                .GET()
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return response.body();
            } else {
                return "Error: " + response.statusCode() + " - " + response.body();
            }
        }  catch (Exception e) {
            return "Unexpected error: " + e.getMessage();
        }
    }
}