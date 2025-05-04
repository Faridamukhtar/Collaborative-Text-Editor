package com.example.application;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.theme.Theme;

import jakarta.annotation.PostConstruct;
import java.awt.Desktop;
import java.net.URI;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The entry point of the Spring Boot application.
 *
 * Use the @PWA annotation make the application installable on phones, tablets
 * and some desktop browsers.
 *
 */
@SpringBootApplication
@Theme(value = "collaborative-text-editor")
public class Application implements AppShellConfigurator {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
    // @PostConstruct
    // public void openBrowser() {
    //     try {
    //         Desktop.getDesktop().browse(new URI("http://localhost:8080"));
    //     } catch (Exception e) {
    //         System.out.println("Could not open browser: " + e.getMessage());
    //     }
    // }
}
