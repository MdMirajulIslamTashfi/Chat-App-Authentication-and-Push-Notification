package com.example.chatApp.dtos.responses;

import com.example.chatApp.enums.Roles;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterResponseDto {
    private boolean success;
    private String status;
    private String message;
    private String email;
    private Roles role;
}
