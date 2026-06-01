package com.example.chatApp.entity;

import com.example.chatApp.enums.MessageType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "chat_messages")
public class ChatMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String fromEmail;
    @Column(nullable = false)
    private String toEmail;
    @Column(nullable = false, length = 2000)
    private String content;

    private LocalDateTime sentAt;
    private LocalDateTime readAt;

    // Relative URL like "/uploads/images/abc123.jpg" — null for text-only messages
    @Convert(converter = com.example.chatApp.config.StringListConverter.class)
    @Column(name = "image_url", columnDefinition = "TEXT")   // reuses existing column name
    @Builder.Default
    private List<String> imageUrls = new ArrayList<>();

    @Builder.Default
    @Column(nullable = false)
    private boolean read = false;

    @Builder.Default
    @Column(nullable = false)
    private boolean deleted = false;

    @Enumerated(EnumType.STRING)
    private MessageType type = MessageType.DIRECT;
}