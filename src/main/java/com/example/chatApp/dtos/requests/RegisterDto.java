package com.example.chatApp.dtos.requests;

import com.example.chatApp.enums.Roles;
import lombok.Data;

@Data
public class RegisterDto {
    private String firstName;
    private String lastName;
    private String email;
    private String password;
    private String confirmPassword;
    private Roles role;
}
