package com.example.chatApp.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

@Slf4j
@Configuration
public class FirebaseConfig {
    // Path to the service account JSON inside src/main/resources/
    @Value("${firebase.service-account-path:firebase-service-account.json}")
    private String serviceAccountPath;

    @PostConstruct
    public void initialize() {
        if (!FirebaseApp.getApps().isEmpty()) return;
        try {
            GoogleCredentials credentials = GoogleCredentials.fromStream(
                    new ClassPathResource(serviceAccountPath).getInputStream()
            );
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(credentials)
                    .build();
            FirebaseApp.initializeApp(options);
            log.info("Firebase Admin SDK initialized.");
        } catch (IOException e) {
            log.warn("Firebase service account not found — push notifications disabled. " +
                    "Add firebase-service-account.json to src/main/resources/");
        }
    }
}