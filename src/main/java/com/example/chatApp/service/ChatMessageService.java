package com.example.chatApp.service;

import com.example.chatApp.dtos.requests.ConversationDto;
import com.example.chatApp.dtos.requests.IncomingMessage;
import com.example.chatApp.dtos.requests.OutgoingMessage;
import com.example.chatApp.entity.ChatMessage;
import com.example.chatApp.entity.User;
import com.example.chatApp.enums.MessageType;
import com.example.chatApp.repositories.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private final ChatMessageRepository repo;
    private final UserService userService;

    /**
     * Saves the message and returns a base DTO.
     * NOTE: the controller is responsible for setting mine=true/false
     * per recipient before dispatching via WebSocket.
     */
    public OutgoingMessage save(IncomingMessage in, String fromEmail) {
        User sender = userService.findByEmail(fromEmail);
        ChatMessage msg = repo.save(ChatMessage.builder()
                .fromEmail(fromEmail)
                .toEmail(in.getToEmail())
                .content(in.getContent())
                .type(MessageType.DIRECT)
                .sentAt(LocalDateTime.now())
                .read(false)
                .build());

        // mine is left as false here — controller overrides it per recipient
        return toDto(msg, sender.fullName(), null);
    }

    /**
     * Full thread history between two users.
     * viewer = the person who requested the thread (their messages get mine=true).
     */
    public List<OutgoingMessage> getThread(String viewer, String other) {
        return repo.findThread(viewer, other).stream()
                .map(m -> toDto(m, userService.findByEmail(m.getFromEmail()).fullName(), viewer))
                .toList();
    }

    // Unread count for a specific thread
    public long unreadCount(String myEmail, String otherEmail) {
        return repo.countUnread(myEmail, otherEmail);
    }

    // Total unread across all threads (dashboard badge)
    public long totalUnread(String myEmail) {
        return repo.countAllUnread(myEmail);
    }

    // All unread messages (notification drawer list)
    public List<OutgoingMessage> allUnread(String myEmail) {
        return repo.findAllUnread(myEmail).stream()
                .map(m -> toDto(m, userService.findByEmail(m.getFromEmail()).fullName(), myEmail))
                .toList();
    }

    // Unread counts per contact (sidebar badges)
    public Map<String, Long> allUnreadCounts(String myEmail) {
        List<User> contacts = userService.getAllExcept(myEmail);
        Map<String, Long> result = new HashMap<>();
        contacts.forEach(u -> {
            long count = repo.countUnread(myEmail, u.getEmail());
            if (count > 0) result.put(u.getEmail(), count);
        });
        return result;
    }

    // Mark all messages from a sender as read
    public void markRead(String reader, String sender) {
        repo.markThreadRead(reader, sender, LocalDateTime.now());
    }

    public List<ConversationDto> getConversations(String myEmail) {
        List<ChatMessage> messages = repo.findByFromEmailOrToEmailOrderBySentAtDesc(myEmail, myEmail);
        Map<String, ConversationDto> map = new LinkedHashMap<>();
        for (ChatMessage msg : messages) {
            String partner = msg.getFromEmail().equals(myEmail) ? msg.getToEmail() : msg.getFromEmail();
            // already added latest message for this partner
            if (map.containsKey(partner)) {
                continue;
            }
            ConversationDto dto = new ConversationDto();
            dto.setEmail(partner);
            dto.setLastMessage(msg.getContent());
            dto.setLastTime(msg.getSentAt());

            long unread = repo.countByToEmailAndFromEmailAndReadFalse(myEmail, partner);
            dto.setUnread(unread);
            map.put(partner, dto);
        }
        return new ArrayList<>(map.values());
    }

    public List<OutgoingMessage> allUnreadFromSender(String myEmail, String sender) {
        return repo.findByToEmailAndFromEmailAndReadFalse(myEmail, sender).stream()
                .map(m -> toDto(m, userService.findByEmail(m.getFromEmail()).fullName(), myEmail))
                .toList();
    }

    /**
     * Converts a ChatMessage entity to an OutgoingMessage DTO.
     * viewer: the email of the person who will receive this DTO.
     * If viewer matches fromEmail → mine=true.
     * Pass null to leave mine=false (controller will set it).
     */
    private OutgoingMessage toDto(ChatMessage m, String fromName, String viewer) {
        boolean mine = viewer != null && m.getFromEmail().equals(viewer);
        return OutgoingMessage.builder()
                .id(m.getId())
                .fromEmail(m.getFromEmail())
                .fromName(fromName)
                .toEmail(m.getToEmail())
                .content(m.getContent())
                .sentAt(m.getSentAt())   // LocalDateTime — serialized as ISO string via @JsonFormat
                .read(m.isRead())
                .mine(mine)
                .build();
    }
}