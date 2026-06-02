package com.example.chatApp.service;

import com.example.chatApp.dtos.DocumentMeta;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;

@Slf4j
@Service
public class DocumentStorageService {

    // ── Allowed MIME types ────────────────────────────────────────────────────
    private static final Set<String> ALLOWED_MIMES = Set.of(
            // Documents
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",   // .docx
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",          // .xlsx
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",  // .pptx
            // Text / data
            "text/plain",
            "text/csv",
            "application/json",
            "application/xml",
            "text/xml",
            // Archives
            "application/zip",
            "application/x-rar-compressed",
            "application/x-7z-compressed",
            // Other common
            "application/rtf",
            "application/vnd.oasis.opendocument.text",          // .odt
            "application/vnd.oasis.opendocument.spreadsheet"    // .ods
    );

    /** Max individual file size: 20 MB */
    private static final long MAX_BYTES = 20L * 1024 * 1024;

    /** Relative URL prefix served by Spring's resource handler */
    private static final String URL_PREFIX = "/uploads/docs/";

    @Value("${app.upload.doc-dir:src/main/resources/static/uploads/docs}")
    private String uploadDir;

    // ── Store one file, return its DocumentMeta ───────────────────────────────
    public DocumentMeta store(MultipartFile file, String uploaderEmail) {
        String mime = file.getContentType() == null ? "" : file.getContentType().toLowerCase();

        if (!ALLOWED_MIMES.contains(mime)) {
            throw new IllegalArgumentException(
                    "File type not allowed: " + mime + ". Supported types: PDF, Word, Excel, PowerPoint, text, CSV, ZIP and more.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException(
                    "File too large. Maximum allowed size is 20 MB.");
        }

        String originalName = sanitizeName(file.getOriginalFilename());

        String extension = extractExtension(originalName);
        String baseName = originalName.contains(".")
                ? originalName.substring(0, originalName.lastIndexOf('.'))
                : originalName;

        String safeEmail = sanitize(uploaderEmail);
        String safeName = sanitize(baseName);

        String timestamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

        String storedName = safeEmail + "_" + safeName + "_" + timestamp +
                (extension.isEmpty() ? "" : "." + extension);

        try {
            Path dir = Paths.get(uploadDir).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            Path dest = dir.resolve(storedName);
            Files.copy(file.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);
            log.info("Document stored: {} → {} by {}", originalName, storedName, uploaderEmail);
        } catch (IOException e) {
            log.error("Failed to store document: {}", e.getMessage());
            throw new RuntimeException("Could not store document. Please try again.", e);
        }

        return new DocumentMeta(originalName, URL_PREFIX + storedName, mime);
    }

    /** Deletes the physical file for a given relative URL (best-effort). */
    public void deleteFile(DocumentMeta meta) {
        if (meta == null || meta.url() == null) return;
        try {
            String filename = meta.url().substring(meta.url().lastIndexOf('/') + 1);
            Path file = Paths.get(uploadDir).toAbsolutePath().normalize().resolve(filename);
            Files.deleteIfExists(file);
            log.info("Document deleted: {}", filename);
        } catch (IOException e) {
            log.warn("Could not delete document file: {}", e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private String sanitize(String input) {
        if (input == null || input.isBlank()) {
            return "unknown";
        }
        return input.replaceAll("[^a-zA-Z0-9]", "_");
    }

    private String extractExtension(String name) {
        int dot = name.lastIndexOf('.');
        return (dot >= 0 && dot < name.length() - 1) ? name.substring(dot + 1) : "";
    }

    private String sanitizeName(String name) {
        if (name == null || name.isBlank()) {
            return "file";
        }

        return Paths.get(name)
                .getFileName()
                .toString();
    }
}