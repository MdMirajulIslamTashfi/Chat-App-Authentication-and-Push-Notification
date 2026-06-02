package com.example.chatApp.config;

import com.example.chatApp.dtos.DocumentMeta;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.ArrayList;
import java.util.List;

@Converter
public class DocumentMetaListConverter implements AttributeConverter<List<DocumentMeta>, String> {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<DocumentMeta> list) {
        if (list == null || list.isEmpty()) return "[]";
        try { return mapper.writeValueAsString(list); }
        catch (Exception e) { return "[]"; }
    }

    @Override
    public List<DocumentMeta> convertToEntityAttribute(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try { return mapper.readValue(json, new TypeReference<>() {}); }
        catch (Exception e) { return new ArrayList<>(); }
    }
}