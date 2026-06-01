package com.example.chatApp.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;

@Slf4j
@Service
public class ImageStorageService {

    @Value("${app.upload.dir:src/main/resources/static/uploads/images}")
    private String uploadDir;

    private static final long MAX_SIZE_BYTES = 1024 * 1024;
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp"
    );

    public String store(MultipartFile file, String uploaderEmail) throws IOException {
        validateFile(file);

        Path dir = Paths.get(uploadDir);
        Files.createDirectories(dir);

        String original  = file.getOriginalFilename();
        String extension = getExtension(original);
        String baseName  = getBaseName(original);
        String safeEmail = sanitize(uploaderEmail);
        String safeName  = sanitize(baseName);

        // Create a safe pattern (yyyyMMdd_HHmmss -> e.g., 20260520_120800)
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
        // Format the current time into a clean String
        String timestamp = LocalDateTime.now().format(formatter);

        String filename = safeEmail + "_" + safeName + "_" + timestamp + extension;
        Path destination = dir.resolve(filename);
        Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);

        log.info("Stored image: {}", destination.toAbsolutePath());
        return "/uploads/images/" + filename;
    }

    // ── NEW ───────────────────────────────────────────────────────────────────
    public void deleteFile(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) return;
        try {
            String filename = imageUrl.substring(imageUrl.lastIndexOf('/') + 1);
            Path file = Paths.get(uploadDir).resolve(filename);
            boolean deleted = Files.deleteIfExists(file);
            if (deleted) log.info("Deleted image: {}", file.toAbsolutePath());
        } catch (IOException e) {
            log.warn("Could not delete image {}: {}", imageUrl, e.getMessage());
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty())
            throw new IllegalArgumentException("File is empty.");
        if (file.getSize() > MAX_SIZE_BYTES)
            throw new IllegalArgumentException("File exceeds the 1 MB limit.");
        String ct = file.getContentType();
        if (ct == null || !ALLOWED_TYPES.contains(ct))
            throw new IllegalArgumentException("Unsupported file type. Allowed: JPEG, PNG, GIF, WEBP.");
    }

    private String sanitize(String input) {
        if (input == null || input.isBlank()) return "unknown";
        return input.replaceAll("[^a-zA-Z0-9]", "_");
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return ".jpg";
        return filename.substring(filename.lastIndexOf('.')).toLowerCase();
    }

    private String getBaseName(String filename) {
        if (filename == null) return "image";
        int dot = filename.lastIndexOf('.');
        String base = dot > 0 ? filename.substring(0, dot) : filename;
        return base.isBlank() ? "image" : base;
    }
}