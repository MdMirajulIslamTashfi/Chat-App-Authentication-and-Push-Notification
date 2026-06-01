package com.example.chatApp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${app.upload.image-dir:uploads/images}")
    private String imageDir;

    @Value("${app.upload.doc-dir:uploads/docs}")
    private String docDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String base = System.getProperty("user.dir") + "/";
        registry.addResourceHandler("/uploads/images/**")
                .addResourceLocations("file:" + base + imageDir + "/");
        registry.addResourceHandler("/uploads/docs/**")
                .addResourceLocations("file:" + base + docDir + "/");
    }
}