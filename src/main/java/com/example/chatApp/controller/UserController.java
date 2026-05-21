package com.example.chatApp.controller;

import com.example.chatApp.dtos.requests.LoginDto;
import com.example.chatApp.dtos.requests.RegisterDto;
import com.example.chatApp.dtos.responses.LoginResponseDto;
import com.example.chatApp.entity.User;
import com.example.chatApp.enums.Roles;
import com.example.chatApp.repositories.UserRepository;
import com.example.chatApp.service.ChatMessageService;
import com.example.chatApp.service.UserService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@Slf4j
@Controller
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    private final ChatMessageService chatMessageService;

    // ------------------------ ROOT redirect -------------------------------
    @GetMapping("/")
    public String root() {
        return "redirect:/login";
    }

    // --------------------------- Register ------------------------------------------------
    @GetMapping("/register")
    public String showRegister(Model model) {
        model.addAttribute("dto", new RegisterDto());
        return "register";
    }

    @PostMapping("/register")
    public String handleRegister(@ModelAttribute RegisterDto dto, Model model) {
        try {
            userService.register(dto);
            return "redirect:/login?registered=true";
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("dto", dto);
            return "register";
        }
    }

    // --------------------------- Login -----------------------------------------------
    @GetMapping("/login")
    public String showLogin(Model model) {
        model.addAttribute("dto", new LoginDto());
        return "login";
    }

    @PostMapping("/login")
    public String handleLogin(@ModelAttribute LoginDto dto,
                              HttpServletResponse response,
                              Model model) {
        try {
            LoginResponseDto result = userService.login(dto);

            // Store token in HttpOnly cookie — no JS needed, no localStorage
            Cookie cookie = new Cookie("jwt_token", result.getToken());
            cookie.setHttpOnly(true);
            cookie.setPath("/");
            // expires when token expires (convert ms → seconds)
            cookie.setMaxAge((int) (result.getExpiresInMs() / 1000));
            response.addCookie(cookie);

            // Redirect based on role
            User user = userService.findByEmail(result.getEmail());
            if (user.getRole() == Roles.ADMIN) {
                return "redirect:/admin/dashboard";
            }
            return "redirect:/user/dashboard";

        } catch (Exception ex) {
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("dto", dto);
            return "login";
        }
    }

    // --------------------------------- Logout ----------------------------
    @PostMapping("/logout")
    public String logout(HttpServletResponse response) {
        Cookie cookie = new Cookie("jwt_token", "");
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
        return "redirect:/login?logout=true";
    }

    // ------------------------- USER DASHBOARD ----------------------------------
    @GetMapping("/user/dashboard")
    public String userDashboard(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        User user = userService.findByEmail(auth.getName());

        model.addAttribute("user", user);

        model.addAttribute(
                "totalUnread",
                chatMessageService.totalUnread(user.getEmail())
        );

        return "user-dashboard";
    }

    // ------------------------- ADMIN DASHBOARD ----------------------------------
    @GetMapping("/admin/dashboard")
    public String adminDashboard(Model model) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        User admin = userService.findByEmail(auth.getName());

        model.addAttribute("admin", admin);

        model.addAttribute(
                "allUsers",
                userService.getAllExcept(admin.getEmail())
        );

        model.addAttribute(
                "totalUsers",
                userService.getAllUsers().size()
        );

        model.addAttribute(
                "totalUnread",
                chatMessageService.totalUnread(admin.getEmail())
        );

        return "admin-dashboard";
    }
}