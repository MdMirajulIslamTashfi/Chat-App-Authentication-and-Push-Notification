package com.example.chatApp.dtos.requests;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConversationDto {
    private String email;
    private String lastMessage;
    private LocalDateTime lastTime;
    private long unread;
}
