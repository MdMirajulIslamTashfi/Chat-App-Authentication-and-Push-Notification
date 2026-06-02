package com.example.chatApp.service;

import com.example.chatApp.dtos.DocumentMeta;
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

    private final ChatMessageRepository  repo;
    private final UserService            userService;
    private final ImageStorageService    imageStorageService;
    private final DocumentStorageService documentStorageService;

    /**
     * Saves the message and returns a base DTO.
     * The controller is responsible for setting mine=true/false per recipient.
     */
    public OutgoingMessage save(IncomingMessage in, String fromEmail) {
        User sender = userService.findByEmail(fromEmail);

        ChatMessage msg = repo.save(ChatMessage.builder()
                .fromEmail(fromEmail)
                .toEmail(in.getToEmail())
                .content(in.getContent() != null ? in.getContent() : null)
                .imageUrls(in.getImageUrls()     != null ? in.getImageUrls()     : new ArrayList<>())
                .documentMetas(in.getDocumentMetas() != null ? in.getDocumentMetas() : new ArrayList<>())
                .type(MessageType.DIRECT)
                .sentAt(LocalDateTime.now())
                .read(false)
                .build());

        // mine is left as false here — controller overrides it per recipient
        return toDto(msg, sender.fullName(), null);
    }

    /** Full thread history between two users. */
    public List<OutgoingMessage> getThread(String viewer, String other) {
        return repo.findThread(viewer, other).stream()
                .map(m -> toDto(m, userService.findByEmail(m.getFromEmail()).fullName(), viewer))
                .toList();
    }

    public long unreadCount(String myEmail, String otherEmail) {
        return repo.countUnread(myEmail, otherEmail);
    }

    public long totalUnread(String myEmail) {
        return repo.countAllUnread(myEmail);
    }

    public List<OutgoingMessage> allUnread(String myEmail) {
        return repo.findAllUnread(myEmail).stream()
                .map(m -> toDto(m, userService.findByEmail(m.getFromEmail()).fullName(), myEmail))
                .toList();
    }

    public Map<String, Long> allUnreadCounts(String myEmail) {
        List<User> contacts = userService.getAllExcept(myEmail);
        Map<String, Long> result = new HashMap<>();
        contacts.forEach(u -> {
            long count = repo.countUnread(myEmail, u.getEmail());
            if (count > 0) result.put(u.getEmail(), count);
        });
        return result;
    }

    public void markRead(String reader, String sender) {
        repo.markThreadRead(reader, sender, LocalDateTime.now());
    }

    public List<ConversationDto> getConversations(String myEmail) {
        List<ChatMessage> messages = repo.findByFromEmailOrToEmailOrderBySentAtDesc(myEmail, myEmail);
        Map<String, ConversationDto> map = new LinkedHashMap<>();

        for (ChatMessage msg : messages) {
            String partner = msg.getFromEmail().equals(myEmail) ? msg.getToEmail() : msg.getFromEmail();
            if (map.containsKey(partner)) continue;

            ConversationDto dto = new ConversationDto();
            dto.setEmail(partner);
            dto.setLastTime(msg.getSentAt());
            dto.setImageUrls(msg.getImageUrls());

            if (msg.isDeleted()) {
                dto.setLastMessage("🚫 This message was deleted");
                dto.setImageUrls(new ArrayList<>());
            } else {
                boolean hasImages = msg.getImageUrls()     != null && !msg.getImageUrls().isEmpty();
                boolean hasDocs   = msg.getDocumentMetas() != null && !msg.getDocumentMetas().isEmpty();
                boolean hasText   = msg.getContent()       != null && !msg.getContent().isBlank();

                if (hasImages && !hasText && !hasDocs) {
                    dto.setLastMessage("📷 " + msg.getImageUrls().size()
                            + (msg.getImageUrls().size() == 1 ? " Image" : " Images"));
                } else if (hasDocs && !hasText && !hasImages) {
                    int cnt = msg.getDocumentMetas().size();
                    dto.setLastMessage("📎 " + cnt + (cnt == 1 ? " File" : " Files"));
                } else {
                    dto.setLastMessage(msg.getContent());
                }
                dto.setImageUrls(msg.getImageUrls());
            }

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
                .imageUrls(m.getImageUrls())
                .documentMetas(m.getDocumentMetas() != null ? m.getDocumentMetas() : new ArrayList<>())
                .sentAt(m.getSentAt())
                .read(m.isRead())
                .deleted(m.isDeleted())
                .mine(mine)
                .build();
    }

    // ── Get recipient email before deletion (used by controller for WS notify) ──
    public String getMessageRecipient(String messageId, String requesterEmail) {
        return repo.findById(messageId)
                .filter(m -> m.getFromEmail().equalsIgnoreCase(requesterEmail))
                .map(ChatMessage::getToEmail)
                .orElse(null);
    }

    // ── Soft-delete — sender only ────────────────────────────────────────────
    public void deleteMessage(String messageId, String requesterEmail) {
        ChatMessage msg = repo.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found."));

        if (!msg.getFromEmail().equalsIgnoreCase(requesterEmail)) {
            throw new com.example.chatApp.exceptionHandler.ForbiddenException(
                    "You can only delete your own messages.");
        }

        // Remove physical image files
        if (msg.getImageUrls() != null) {
            msg.getImageUrls().forEach(imageStorageService::deleteFile);
        }

        // Remove physical document files
        if (msg.getDocumentMetas() != null) {
            msg.getDocumentMetas().forEach(documentStorageService::deleteFile);
        }

        // Soft delete: keep the row, clear content, mark deleted
        msg.setDeleted(true);
        msg.setContent("");
        msg.setImageUrls(new ArrayList<>());
        msg.setDocumentMetas(new ArrayList<>());
        repo.save(msg);

        log.info("Message {} soft-deleted by {}", messageId, requesterEmail);
    }
}