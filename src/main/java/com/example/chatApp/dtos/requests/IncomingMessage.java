package com.example.chatApp.dtos.requests;

import com.example.chatApp.dtos.DocumentMeta;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class IncomingMessage {
    private String toEmail;
    private String content;

    private List<String> imageUrls = new ArrayList<>();
    private List<DocumentMeta> documentMetas = new ArrayList<>();
}