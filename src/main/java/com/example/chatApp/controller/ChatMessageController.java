package com.example.chatApp.controller;

import com.example.chatApp.dtos.DocumentMeta;
import com.example.chatApp.dtos.requests.IncomingMessage;
import com.example.chatApp.dtos.requests.OutgoingMessage;
import com.example.chatApp.entity.User;
import com.example.chatApp.enums.Roles;
import com.example.chatApp.repositories.UserRepository;
import com.example.chatApp.service.ChatMessageService;
import com.example.chatApp.service.DocumentStorageService;
import com.example.chatApp.service.FirebaseNotificationService;
import com.example.chatApp.service.ImageStorageService;
import com.example.chatApp.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatMessageController {

    private final UserService            userService;
    private final ChatMessageService     chatService;
    private final SimpMessagingTemplate  broker;
    private final FirebaseNotificationService fcmService;
    private final UserRepository         userRepository;
    private final ImageStorageService    imageStorageService;
    private final DocumentStorageService documentStorageService;

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

    // ── Quick Reply Admin check ────────────────────────────────────────────
    @GetMapping("/api/admins")
    @ResponseBody
    public ResponseEntity<?> admins() {
        List<User> admins = userRepository.findByRole(Roles.ADMIN);
        return ResponseEntity.ok(
                admins.stream().map(a -> Map.of("email", a.getEmail(), "name", a.fullName())).toList()
        );
    }

    // ── WebSocket: handle incoming direct message ──────────────────────────
    @MessageMapping("/chat.send")
    public void handleMessage(@Payload IncomingMessage incoming, Principal principal) {
        String fromEmail = principal.getName();
        log.info("WS message {} -> {}", fromEmail, incoming.getToEmail());

        if (fromEmail.equalsIgnoreCase(incoming.getToEmail())) return;

        OutgoingMessage base = chatService.save(incoming, fromEmail);

        OutgoingMessage forRecipient = base.toBuilder().mine(false).build();
        broker.convertAndSendToUser(incoming.getToEmail(), "/queue/messages", forRecipient);

        OutgoingMessage forSender = base.toBuilder().mine(true).build();
        broker.convertAndSendToUser(fromEmail, "/queue/messages", forSender);

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
        return ResponseEntity.ok(chatService.getThread(myEmail, with));
    }

    // ── REST: unread count for one thread ─────────────────────────────────
    @GetMapping("/api/chat/unread")
    @ResponseBody
    public ResponseEntity<?> unread(@RequestParam String with) {
        return ResponseEntity.ok(Map.of("count", chatService.unreadCount(getEmail(), with)));
    }

    // ── REST: unread counts for ALL threads ───────────────────────────────
    @GetMapping("/api/chat/unread-all")
    @ResponseBody
    public ResponseEntity<?> unreadAll() {
        return ResponseEntity.ok(chatService.allUnreadCounts(getEmail()));
    }

    // ── REST: total unread count ───────────────────────────────────────────
    @GetMapping("/api/chat/unread-total")
    @ResponseBody
    public ResponseEntity<?> unreadTotal() {
        return ResponseEntity.ok(Map.of("total", chatService.totalUnread(getEmail())));
    }

    // ── REST: all unread messages list ────────────────────────────────────
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
        List<OutgoingMessage> unread = chatService.allUnreadFromSender(me, sender);
        chatService.markRead(me, sender);
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
        broker.convertAndSendToUser(
                receipt.sender(),
                "/queue/read",
                Map.of("messageId", receipt.messageId(),
                        "readAt", java.time.LocalDateTime.now().toString())
        );
    }

    // ── REST: conversations list ───────────────────────────────────────────
    @GetMapping("/api/chat/conversations")
    @ResponseBody
    public ResponseEntity<?> conversations() {
        return ResponseEntity.ok(chatService.getConversations(getEmail()));
    }

    // ── REST: upload image(s) ──────────────────────────────────────────────
    @PostMapping("/api/chat/upload-image")
    @ResponseBody
    public ResponseEntity<?> uploadImage(@RequestParam("files") List<MultipartFile> files,
                                         Authentication authentication) {
        try {
            List<String> urls = new ArrayList<>();
            for (MultipartFile file : files) {
                urls.add(imageStorageService.store(file, authentication.getName()));
            }
            return ResponseEntity.ok(Map.of("imageUrls", urls));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Image upload failed: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", "Upload failed. Please try again."));
        }
    }

    /**
     * POST /api/chat/upload-document
     * Accepts one or more files, stores them, returns their metadata list
     * (name, url, mime) which the client passes back via WebSocket.
     */
    @PostMapping("/api/chat/upload-document")
    @ResponseBody
    public ResponseEntity<?> uploadDocument(@RequestParam("files") List<MultipartFile> files,
                                            Authentication authentication) {
        try {
            List<DocumentMeta> metas = new ArrayList<>();
            for (MultipartFile file : files) {
                metas.add(documentStorageService.store(file, authentication.getName()));
            }
            return ResponseEntity.ok(Map.of("documentMetas", metas));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Document upload failed: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", "Upload failed. Please try again."));
        }
    }

    // ── REST: delete message ───────────────────────────────────────────────
    @DeleteMapping("/api/chat/message/{id}")
    @ResponseBody
    public ResponseEntity<?> deleteMessage(@PathVariable String id) {
        String myEmail = getEmail();
        try {
            String recipientEmail = chatService.getMessageRecipient(id, myEmail);
            chatService.deleteMessage(id, myEmail);
            if (recipientEmail != null) {
                broker.convertAndSendToUser(recipientEmail, "/queue/deleted", Map.of("messageId", id));
            }
            return ResponseEntity.ok(Map.of("status", "deleted", "messageId", id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (com.example.chatApp.exceptionHandler.ForbiddenException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Delete message failed: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", "Could not delete message."));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────
    private String getEmail() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    public record ReadReceipt(String messageId, String sender) {}
}