package com.example.chatApp.config;

import lombok.RequiredArgsConstructor;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Automatically injects Firebase config into every Thymeleaf model,
 * so user-dashboard.html and any other page can access it without
 * duplicating code in every controller method.
 */
@ControllerAdvice
@RequiredArgsConstructor
public class FirebaseModelAdvice {

    private final FirebaseProperties firebaseProperties;

    @ModelAttribute
    public void addFirebaseConfig(Model model) {
        model.addAttribute("fbApiKey", firebaseProperties.getApiKey());
        model.addAttribute("fbAuthDomain", firebaseProperties.getAuthDomain());
        model.addAttribute("fbProjectId", firebaseProperties.getProjectId());
        model.addAttribute("fbStorageBucket", firebaseProperties.getStorageBucket());
        model.addAttribute("fbMessagingSenderId", firebaseProperties.getMessagingSenderId());
        model.addAttribute("fbAppId", firebaseProperties.getAppId());
        model.addAttribute("fbVapidKey", firebaseProperties.getVapidKey());
    }
}
