package com.example.chatApp.dtos.requests;

import lombok.Data;

@Data
public class IncomingMessage {
    private String toEmail;
    private String content;
}