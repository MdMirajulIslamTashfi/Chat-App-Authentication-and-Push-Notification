package com.example.chatApp.controller;

import com.example.chatApp.dtos.requests.IncomingMessage;
import com.example.chatApp.dtos.requests.OutgoingMessage;
import com.example.chatApp.entity.User;
import com.example.chatApp.repositories.UserRepository;
import com.example.chatApp.service.ChatMessageService;
import com.example.chatApp.service.FirebaseNotificationService;
import com.example.chatApp.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatMessageController {

    private final UserService userService;
    private final ChatMessageService chatService;
    private final SimpMessagingTemplate broker;
    private final FirebaseNotificationService fcmService;
    private final UserRepository userRepository;

    // ── User chat page ─────────────────────────────────────────────────────
    @GetMapping("/user/chat")
    public String userChat(Model model) {
        String myEmail = getEmail();
        User me = userService.findByEmail(myEmail);
        List<User> contacts = userService.getAllExcept(myEmail);
        model.addAttribute("me", me);
        model.addAttribute("contacts", contacts);
        model.addAttribute("unreadCounts", chatService.allUnreadCounts(myEmail));
        return "chat";
    }

    // ── Admin chat page ────────────────────────────────────────────────────
    @GetMapping("/admin/chat")
    public String adminChat(Model model) {
        String myEmail = getEmail();
        User me = userService.findByEmail(myEmail);
        List<User> contacts = userService.getAllExcept(myEmail);
        model.addAttribute("me", me);
        model.addAttribute("contacts", contacts);
        model.addAttribute("unreadCounts", chatService.allUnreadCounts(myEmail));
        return "chat";
    }

    // ── WebSocket: handle incoming direct message ──────────────────────────
    @MessageMapping("/chat.send")
    public void handleMessage(@Payload IncomingMessage incoming, Principal principal) {
        String fromEmail = principal.getName();
        log.info("WS message {} -> {}", fromEmail, incoming.getToEmail());

        if (fromEmail.equalsIgnoreCase(incoming.getToEmail())) {
            return;
        }

        // Save once, get the base DTO (mine is irrelevant here — we override below)
        OutgoingMessage base = chatService.save(incoming, fromEmail);

        // Send to RECIPIENT with mine=false (they are receiving it)
        OutgoingMessage forRecipient = base.toBuilder().mine(false).build();
        broker.convertAndSendToUser(incoming.getToEmail(), "/queue/messages", forRecipient);

        // Echo back to SENDER with mine=true (they sent it)
        OutgoingMessage forSender = base.toBuilder().mine(true).build();
        broker.convertAndSendToUser(fromEmail, "/queue/messages", forSender);

        // Firebase push notification to recipient
        userRepository.findByEmail(incoming.getToEmail()).ifPresent(recipient -> {
            if (recipient.getFcmToken() != null) {
                User sender = userService.findByEmail(fromEmail);
                fcmService.sendPushNotification(
                        recipient.getFcmToken(),
                        "New message from " + sender.fullName(),
                        incoming.getContent()
                );
            }
        });
    }

    // ── REST: load thread history ──────────────────────────────────────────
    @GetMapping("/api/chat/thread")
    @ResponseBody
    public ResponseEntity<?> getThread(@RequestParam String with) {
        String myEmail = getEmail();
        chatService.markRead(myEmail, with);
        return ResponseEntity.ok(chatService.getThread(myEmail, with));
    }

    // ── REST: unread count for one thread ─────────────────────────────────
    @GetMapping("/api/chat/unread")
    @ResponseBody
    public ResponseEntity<?> unread(@RequestParam String with) {
        return ResponseEntity.ok(Map.of("count", chatService.unreadCount(getEmail(), with)));
    }

    // ── REST: unread counts for ALL threads (for sidebar badges) ──────────
    @GetMapping("/api/chat/unread-all")
    @ResponseBody
    public ResponseEntity<?> unreadAll() {
        return ResponseEntity.ok(chatService.allUnreadCounts(getEmail()));
    }

    // ── REST: total unread count (for nav bell badge) ──────────────────────
    @GetMapping("/api/chat/unread-total")
    @ResponseBody
    public ResponseEntity<?> unreadTotal() {
        return ResponseEntity.ok(Map.of("total", chatService.totalUnread(getEmail())));
    }

    // ── REST: all unread messages list (for notification drawer) ──────────
    @GetMapping("/api/chat/unread-list")
    @ResponseBody
    public ResponseEntity<?> unreadList() {
        return ResponseEntity.ok(chatService.allUnread(getEmail()));
    }

    // ── REST: mark thread read ─────────────────────────────────────────────
    @PostMapping("/api/chat/read")
    @ResponseBody
    public ResponseEntity<?> markRead(@RequestParam String sender) {
        String me = getEmail();
        List<OutgoingMessage> unread =
                chatService.allUnreadFromSender(me, sender);
        // Notify the sender that their messages were read (for tick upgrade)
        chatService.markRead(me, sender);

        unread.forEach(msg -> {

            broker.convertAndSendToUser(
                    sender,
                    "/queue/read",
                    Map.of(
                            "messageId", msg.getId(),
                            "reader", me
                    )
            );
        });

        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    // ── REST: save FCM token ───────────────────────────────────────────────
    @PostMapping("/fcm/token")
    @ResponseBody
    public ResponseEntity<?> saveFcmToken(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        if (token != null && !token.isBlank()) {
            userRepository.findByEmail(getEmail()).ifPresent(u -> {
                u.setFcmToken(token);
                userRepository.save(u);
            });
        }
        return ResponseEntity.ok(Map.of("status", "saved"));
    }

    // ── WebSocket: read receipt ────────────────────────────────────────────
    @MessageMapping("/chat.read")
    public void readReceipt(@Payload ReadReceipt receipt, Principal principal) {
        // Tell the original sender their message was read
        broker.convertAndSendToUser(
                receipt.sender(),
                "/queue/read",
                Map.of(
                        "messageId", receipt.messageId(),
                        "readAt", java.time.LocalDateTime.now().toString()
                )
        );
    }


    // -----------------------------load conversation ---------------------
    @GetMapping("/api/chat/conversations")
    @ResponseBody
    public ResponseEntity<?> conversations() {
        return ResponseEntity.ok(
                chatService.getConversations(getEmail())
        );
    }


    private String getEmail() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    public record ReadReceipt(String messageId, String sender) {}
}