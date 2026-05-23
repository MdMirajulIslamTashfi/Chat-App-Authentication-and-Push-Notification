package com.example.chatApp.eventListener;

import com.example.chatApp.service.UserPresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class PresenceEventListener {

    private final UserPresenceService presenceService;
    private final SimpMessagingTemplate messagingTemplate;

    @EventListener
    public void handleConnect(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String email = getPrincipalEmail(accessor);
        if (email == null) return;

        presenceService.setOnline(email);
        log.info("User online: {}", email);
        broadcast(email, "ONLINE");
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String email = getPrincipalEmail(accessor);
        if (email == null) return;

        presenceService.setOffline(email);
        log.info("User offline: {}", email);
        broadcast(email, "OFFLINE");
    }

    private void broadcast(String email, String status) {
        // Broadcast to /topic/presence so all connected clients receive it
        messagingTemplate.convertAndSend("/topic/presence", (Object) Map.of("email", email, "status", status));
    }

    private String getPrincipalEmail(StompHeaderAccessor accessor) {
        if (accessor.getUser() == null) return null;
        return accessor.getUser().getName();
    }
}
