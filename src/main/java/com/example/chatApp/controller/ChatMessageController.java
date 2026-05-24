package com.example.chatApp.controller;

import com.example.chatApp.dtos.requests.IncomingMessage;
import com.example.chatApp.dtos.requests.OutgoingMessage;
import com.example.chatApp.entity.User;
import com.example.chatApp.enums.Roles;
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
        User admin = userRepository.findFirstByRole(Roles.ADMIN).orElseThrow();
        model.addAttribute("adminEmail", admin.getEmail());

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
        User admin = userRepository.findFirstByRole(Roles.ADMIN).orElseThrow();
        model.addAttribute("adminEmail", admin.getEmail());
        return "chat";
    }

    // Quick Reply Admin check
    @GetMapping("/api/admins")
    @ResponseBody
    public ResponseEntity<?> admins() {
        List<User> admins = userRepository.findByRole(Roles.ADMIN);

        return ResponseEntity.ok(
                admins.stream().map(a -> Map.of(
                        "email", a.getEmail(),
                        "name", a.fullName()
                )).toList()
        );
    }

    // ── WebSocket: handle incoming direct message ──────────────────────────
    @MessageMapping("/chat.send")
    public void handleMessage(@Payload IncomingMessage incoming, Principal principal) {
        String fromEmail = principal.getName();
        log.info("WS message {} -> {}", fromEmail, incoming.getToEmail());

        // Simple guard rail: Stops a user from executing an infinite feedback loop by texting themselves
        if (fromEmail.equalsIgnoreCase(incoming.getToEmail())) return;

        // Save once, get the base DTO (mine is irrelevant here — we override below)
        // Commits the message body to the persistent database, returning the foundational saved object state
        OutgoingMessage base = chatService.save(incoming, fromEmail);

        // Send to RECIPIENT with mine=false (they are receiving it)
        // Clones payload with 'mine=false' -> sends to the recipient so their UI styles it as an *incoming* speech bubble
        OutgoingMessage forRecipient = base.toBuilder().mine(false).build();
        broker.convertAndSendToUser(incoming.getToEmail(), "/queue/messages", forRecipient);

        // Echo back to SENDER with mine=true (they sent it)
        // Clones payload with 'mine=true' -> bounces it back to the sender so their UI can render an *outgoing* blue/green bubble instantly
        OutgoingMessage forSender = base.toBuilder().mine(true).build();
        broker.convertAndSendToUser(fromEmail, "/queue/messages", forSender);

        // Firebase push notification to recipient
        // Look up the target recipient profile inside the database
        userRepository.findByEmail(incoming.getToEmail()).ifPresent(recipient -> {
            // Checks if the receiver has an active Firebase Cloud Messaging mobile/web push registration device token
            if (recipient.getFcmToken() != null) {
                User sender = userService.findByEmail(fromEmail);
                // Delivers a native mobile lockscreen/web push notification out-of-band via Google FCM servers
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
    // Tells Spring to treat the returned data object directly as raw JSON body data rather than trying to look up a webpage template
    public ResponseEntity<?> getThread(@RequestParam String with) {
        String myEmail = getEmail();
        return ResponseEntity.ok(chatService.getThread(myEmail, with)); // Fetches historical logs for this chat window
    }

    // ── REST: unread count for one thread ─────────────────────────────────
    @GetMapping("/api/chat/unread")
    @ResponseBody
    public ResponseEntity<?> unread(@RequestParam String with) {
        // Wraps an integer directly inside a simple Map structure to output dynamic JSON cleanly like {"count": 4}
        return ResponseEntity.ok(Map.of("count", chatService.unreadCount(getEmail(), with)));
    }

    // ── REST: unread counts for ALL threads (for sidebar badges) ──────────
    @GetMapping("/api/chat/unread-all")
    @ResponseBody
    public ResponseEntity<?> unreadAll() {
        return ResponseEntity.ok(chatService.allUnreadCounts(getEmail())); // Returns Map linking user email keys to integer badge counts
    }

    // ── REST: total unread count (for nav bell badge) ──────────────────────
    @GetMapping("/api/chat/unread-total")
    @ResponseBody
    public ResponseEntity<?> unreadTotal() {
        // Yields summary total across all contacts combined, perfect for top-navigation headers or notification bell icons
        return ResponseEntity.ok(Map.of("total", chatService.totalUnread(getEmail())));
    }

    // ── REST: all unread messages list (for notification drawer) ──────────
    @GetMapping("/api/chat/unread-list")
    @ResponseBody
    public ResponseEntity<?> unreadList() {
        return ResponseEntity.ok(chatService.allUnread(getEmail())); // Fetches full collection list arrays containing unread items
    }

    // ── REST: mark thread read ─────────────────────────────────────────────
    @PostMapping("/api/chat/read")
    @ResponseBody
    public ResponseEntity<?> markRead(@RequestParam String sender) {
        String me = getEmail();
        // Isolates the array snapshot matching all existing message entries before we apply the DB update flags
        List<OutgoingMessage> unread = chatService.allUnreadFromSender(me, sender);

        // Notify the sender that their messages were read (for tick upgrade)
        // Executes persistence database upgrade mutating targeted message states to read statuses
        chatService.markRead(me, sender);

        // Iterates through every freshly cleared item and pushes a real-time event to the sender's client session
        // This instantly changes single checkmarks to blue double checkmarks (read receipts) on their screen
        unread.forEach(msg -> {
            broker.convertAndSendToUser(sender, "/queue/read", Map.of("messageId", msg.getId(), "reader", me));
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
        // Forwards a message directly to the target sender's listening session informing them their specific ID asset has been viewed
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
        // Fetches an aggregated high-level dashboard feed displaying active recent dialogue channels
        return ResponseEntity.ok(chatService.getConversations(getEmail()));
    }

    // Helper utility pattern resolving identity details out of Spring Security context
    private String getEmail() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    // Java Record type: A lightweight data-carrier capsule structure used to rapidly map incoming JSON fields for read-receipt payloads
    public record ReadReceipt(String messageId, String sender) {
    }
    private String conflict (){
        return "conflict in image";
    }
}