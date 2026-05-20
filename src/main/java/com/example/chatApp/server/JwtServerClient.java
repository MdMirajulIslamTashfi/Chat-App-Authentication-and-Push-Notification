package com.example.chatApp.server;


import com.example.chatApp.exceptionHandler.JwtServerException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtServerClient {
    private final RestTemplate restTemplate;

    // Jwt authentication server on port 8081
    public final String generateJwtUrl = "http://localhost:8081/api/jwt/generate";
    public final String validateJwtUrl = "http://localhost:8081/api/jwt/validate";

    // asking jwtServer to generate token
    public TokenResult generateToken(String email, String password, String role) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> map = Map.of("email", email, "password", password, "role", role);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(map, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(generateJwtUrl, entity, Map.class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Object token = response.getBody().get("token");
                long expiresInMs = getLong(response.getBody(), "expiresInMs");
                if (token != null) return new TokenResult(token.toString(), expiresInMs);
                throw new JwtServerException("jwt-server response missing 'token' field");
            }
            throw new JwtServerException("jwt-server returned status: " + response.getStatusCode());
        } catch (ResourceAccessException ex) {
            log.error("jwt-server unreachable at {}", generateJwtUrl);
            throw new JwtServerException("jwt-server is not reachable. Make sure it is running.");
        } catch (JwtServerException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to generate token: {}", ex.getMessage());
            throw new JwtServerException("Token generation failed: " + ex.getMessage());
        }
    }

    //token validation

    public ValidateResult validateToken(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> body = Map.of("token", token);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    validateJwtUrl, toEntity(body), Map.class);

            Map<?, ?> resp = response.getBody();
            boolean valid = Boolean.TRUE.equals(resp.get("valid"));
            String email = resp.get("email") != null ? resp.get("email").toString() : null;
            String role = resp.get("role") != null ? resp.get("role").toString() : null;
            String message = resp.get("message") != null ? resp.get("message").toString() : null;

            return new ValidateResult(valid, message, email, role);

        } catch (ResourceAccessException ex) {
            log.error("jwt-server is unreachable at {}", validateJwtUrl);
            throw new JwtServerException("jwt-server is not reachable. Make sure it is running on port 9090.");
        } catch (JwtServerException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Token validation failed: {}", ex.getMessage());
            throw new JwtServerException(ex.getMessage());
        }
    }

    // Helper method
    private HttpEntity<Object> toEntity(Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private Map<?, ?> requireOk(ResponseEntity<Map> response, String action) {
        if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
            throw new JwtServerException("Unexpected response from jwt-server during " + action);
        }
        return response.getBody();
    }

    private String getString(Map<?, ?> map, String key) {
        Object val = map.get(key);
        if (val == null) throw new JwtServerException("Response missing field: " + key);
        return val.toString();
    }

    private long getLong(Map<?, ?> map, String key) {
        Object val = map.get(key);
        if (val == null) return 0L;
        return ((Number) val).longValue();
    }

    // Records returned to AuthService
    public record TokenResult(String token, long expiresInMs) {
    }

    public record ValidateResult(boolean valid, String message, String email, String role) {
    }
}
