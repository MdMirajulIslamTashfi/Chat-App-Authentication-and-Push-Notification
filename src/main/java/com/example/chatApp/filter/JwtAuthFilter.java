package com.example.chatApp.filter;


import com.example.chatApp.server.JwtServerClient;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtServerClient jwtServerClient;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String token = extractToken(request);

        if (token == null) {
            // No token at all → pass through, Spring Security handles 401/403
            filterChain.doFilter(request, response);
            return;
        }

        try {
            JwtServerClient.ValidateResult result = jwtServerClient.validateToken(token);

            if (result.valid() && result.role() != null) {
                // "ROLE_USER" or "ROLE_ADMIN" — Spring Security prefix convention
                String roleWithPrefix = result.role().startsWith("ROLE_")
                        ? result.role()
                        : "ROLE_" + result.role();

                SimpleGrantedAuthority authority = new SimpleGrantedAuthority(roleWithPrefix);

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                result.email(),
                                null,
                                List.of(authority)
                        );

                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.info("Token valid → email={} role={}", result.email(), result.role());

            } else {
                log.warn("Token invalid → {}", result.message());
            }

        } catch (Exception ex) {
            // JWT server down or token malformed — don't block, let Spring Security decide
            log.error("Token validation error: {}", ex.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Priority 1: HttpOnly cookie "jwt_token"   (browser-based MVC flow)
     * Priority 2: Authorization: Bearer header  (API / Postman flow)
     */
    private String extractToken(HttpServletRequest request) {
        // 1. Cookie
        if (request.getCookies() != null) {
            return Arrays.stream(request.getCookies())
                    .filter(c -> "jwt_token".equals(c.getName()))
                    .map(Cookie::getValue)
                    .filter(v -> v != null && !v.isBlank())
                    .findFirst()
                    .orElse(tryHeader(request));
        }
        // 2. Header fallback
        return tryHeader(request);
    }

    private String tryHeader(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}