package com.example.chatApp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${app.upload.image-dir}")
    private String imageDir;

    @Value("${app.upload.doc-dir}")
    private String docDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/images/**")
                .addResourceLocations("file:" + imageDir + "/");
        registry.addResourceHandler("/uploads/docs/**")
                .addResourceLocations("file:" + docDir + "/");
    }
}