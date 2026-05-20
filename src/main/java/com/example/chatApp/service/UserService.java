package com.example.chatApp.service;

import com.example.chatApp.dtos.requests.LoginDto;
import com.example.chatApp.dtos.requests.RegisterDto;
import com.example.chatApp.dtos.responses.LoginResponseDto;
import com.example.chatApp.dtos.responses.RegisterResponseDto;
import com.example.chatApp.entity.User;
import com.example.chatApp.enums.MessageType;
import com.example.chatApp.enums.Roles;
import com.example.chatApp.exceptionHandler.EmailAlreadyExistsException;
import com.example.chatApp.exceptionHandler.InvalidPasswordException;
import com.example.chatApp.exceptionHandler.UserNotFoundException;
import com.example.chatApp.repositories.UserRepository;
import com.example.chatApp.server.JwtServerClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final JwtServerClient jwtServerClient;

    // ── REGISTER ──────────────────────────────────────────────────────────────
    public RegisterResponseDto register(RegisterDto dto) {
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new EmailAlreadyExistsException("Email already registered: " + dto.getEmail());
        }

        if (!dto.getPassword().equals(dto.getConfirmPassword())) {
            throw new InvalidPasswordException("Passwords don't match");
        }

        User user = userRepository.save(User.builder()
                .firstName(dto.getFirstName())
                .lastName(dto.getLastName())
                .email(dto.getEmail())
                .password(dto.getPassword())
                .createdAt(LocalDateTime.now())
                .role(dto.getRole() != null ? dto.getRole() : Roles.USER)
                .build());

        log.info("Registered new user: {}", user.getEmail());

        return RegisterResponseDto.builder()
                .success(true)
                .status("201")
                .message("Registration successful. Please log in.")
                .email(user.getEmail())
                .role(user.getRole())
                .build();
    }

    // ── LOGIN ─────────────────────────────────────────────────────────────────
    public LoginResponseDto login(LoginDto dto) {
        User user = userRepository.findByEmail(dto.getEmail()).orElseThrow(() -> new UserNotFoundException("No account found for: " + dto.getEmail()));

        if (!user.getPassword().equals(dto.getPassword())) {
            throw new InvalidPasswordException("Incorrect password.");
        }

        JwtServerClient.TokenResult tokenResult = jwtServerClient.generateToken(user.getEmail(), dto.getPassword(), user.getRole().name());

        log.info("User credentials verified: {}", user.getEmail());

        return LoginResponseDto.builder()
                .success(true)
                .status("200")
                .message("Credentials verified.")
                .email(user.getEmail())
                .token(tokenResult.token())
                .expiresInMs(tokenResult.expiresInMs())
                .build();
    }

    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + email));
    }

    // Everyone except self — for contact list (users + admins)
    public List<User> getAllExcept(String email) {
        return userRepository.findAll().stream()
                .filter(u -> !u.getEmail().equals(email))
                .toList();
    }

    public List<User> getAllUsers() {
        return userRepository.findByRoleNot(Roles.ADMIN);
    }

    public List<User> getAllAdmins() {
        return userRepository.findByRole(Roles.ADMIN);
    }

}
