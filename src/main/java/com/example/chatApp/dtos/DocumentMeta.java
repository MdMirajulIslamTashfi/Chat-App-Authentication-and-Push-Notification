package com.example.chatApp.dtos;

/**
 * Lightweight carrier stored as JSON inside the chat_messages.document_urls column.
 * name  — original filename shown in the bubble
 * url   — relative path served by the app  (e.g. "/uploads/docs/abc123.pdf")
 * mime  — MIME type used to pick the right icon on the frontend
 */
public record DocumentMeta(String name, String url, String mime) {}