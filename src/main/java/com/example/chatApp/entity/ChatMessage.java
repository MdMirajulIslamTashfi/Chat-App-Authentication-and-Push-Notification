package com.example.chatApp.entity;

import com.example.chatApp.enums.MessageType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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

    @Builder.Default
    @Column(nullable = false)
    private boolean read = false;

    @Enumerated(EnumType.STRING)
    private MessageType type = MessageType.DIRECT;
}