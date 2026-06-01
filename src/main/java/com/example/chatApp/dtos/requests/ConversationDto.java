package com.example.chatApp.dtos.requests;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ConversationDto {
    private String email;
    private String lastMessage;
    private LocalDateTime lastTime;
    private long unread;
    private List<String> imageUrls;
}
