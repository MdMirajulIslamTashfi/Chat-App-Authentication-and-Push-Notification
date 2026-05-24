package com.example.chatApp.controller;

import com.example.chatApp.service.UserPresenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/api/presence")
@RequiredArgsConstructor
public class PresenceController {
    private final UserPresenceService userPresenceService;

    @GetMapping("/online")
    public Set<String> getOnlineUsers() {
        return userPresenceService.getOnlineUsers();
    }
}
