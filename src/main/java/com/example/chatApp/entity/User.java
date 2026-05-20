package com.example.chatApp.entity;

import com.example.chatApp.enums.Roles;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    private String firstName;
    private String lastName;
    private String email;
    private String password;
    private LocalDateTime createdAt;
    private String fcmToken;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Roles role;

    // ── Convenience ───────────────────────────────────────────────────────
    public String fullName() {
        return firstName + " " + lastName;
    }

    public String initials() {
        String f = (firstName != null && !firstName.isEmpty()) ? String.valueOf(firstName.charAt(0)) : "";
        String l = (lastName  != null && !lastName.isEmpty())  ? String.valueOf(lastName.charAt(0))  : "";
        return (f + l).toUpperCase();
    }
}