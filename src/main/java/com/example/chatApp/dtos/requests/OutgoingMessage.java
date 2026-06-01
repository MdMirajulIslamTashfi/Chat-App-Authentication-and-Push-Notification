package com.example.chatApp.dtos.requests;

import com.example.chatApp.dtos.DocumentMeta;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class OutgoingMessage {
    private String id;
    private String fromEmail;
    private String fromName;
    private String toEmail;
    private String content;

    /** Image attachment URLs */
    private List<String> imageUrls;
    /**
     * Document attachments — each entry carries original filename, relative URL,
     * and MIME type so the frontend can render the right icon and download link.
     */
    @Builder.Default
    private List<DocumentMeta> documentMetas = new ArrayList<>();

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime sentAt;

    private boolean read;
    public  boolean deleted;
    private boolean mine;
}