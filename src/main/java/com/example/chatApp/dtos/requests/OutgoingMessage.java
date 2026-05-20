package com.example.chatApp.dtos.requests;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;

@Data
@Builder (toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class OutgoingMessage {
    private String id;
    private String fromEmail;
    private String fromName;
    private String toEmail;
    private String content;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") // Serialize LocalDateTime as "2026-05-20T12:08:00" — ISO string JS can parse
    private LocalDateTime sentAt;
    private boolean read;
    private boolean mine; // true when the receiver is also the sender (echo)
}
