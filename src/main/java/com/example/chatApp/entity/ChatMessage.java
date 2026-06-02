package com.example.chatApp.entity;

import com.example.chatApp.config.DocumentMetaListConverter;
import com.example.chatApp.config.StringListConverter;
import com.example.chatApp.dtos.DocumentMeta;
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

    /** Relative image URLs — e.g. ["/uploads/images/abc.jpg"] */
    @Convert(converter = StringListConverter.class)
    @Column(name = "image_url", columnDefinition = "TEXT")
    @Builder.Default
    private List<String> imageUrls = new ArrayList<>();

    /**
     * Document attachments stored as JSON array of {name, url, mime}.
     * Column added via migration:
     *   ALTER TABLE chat_messages ADD COLUMN document_metas TEXT DEFAULT '[]';
     */
    @Convert(converter = DocumentMetaListConverter.class)
    @Column(name = "document_metas", columnDefinition = "TEXT")
    @Builder.Default
    private List<DocumentMeta> documentMetas = new ArrayList<>();

    @Builder.Default
    @Column(nullable = false)
    private boolean read = false;

    @Builder.Default
    @Column(nullable = false)
    private boolean deleted = false;

    @Enumerated(EnumType.STRING)
    private MessageType type = MessageType.DIRECT;
}