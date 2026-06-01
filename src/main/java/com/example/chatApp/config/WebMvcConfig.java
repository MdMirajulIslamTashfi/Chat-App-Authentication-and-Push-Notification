package com.example.chatApp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${app.upload.dir:src/main/resources/static/uploads/images}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Serve uploaded images from the filesystem path directly.
        // "file:" prefix tells Spring to read from disk, not the classpath.
        // The trailing slash on both sides is required.
        registry.addResourceHandler("/uploads/images/**")
                .addResourceLocations("file:" + uploadDir + "/");
    }
}