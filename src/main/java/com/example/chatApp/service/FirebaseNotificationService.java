package com.example.chatApp.service;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class FirebaseNotificationService {

    /**
     * Sends a push notification to a specific device via its FCM token.
     * Silently skips if Firebase is not initialized or token is blank.
     */
    public void sendPushNotification(String fcmToken, String title, String body) {
        if (fcmToken == null || fcmToken.isBlank()) return;
        if (FirebaseApp.getApps().isEmpty()) {
            log.debug("Firebase not initialized — skipping push notification.");
            return;
        }
        try {
            Message message = Message.builder()
                    .setToken(fcmToken)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .putData("click_action", "/user/chat")
                    .build();

            String response = FirebaseMessaging.getInstance().send(message);
            log.info("FCM sent: {}", response);
        } catch (FirebaseMessagingException e) {
            log.error("FCM error ({}): {}", e.getMessagingErrorCode(), e.getMessage());
        }
    }
}