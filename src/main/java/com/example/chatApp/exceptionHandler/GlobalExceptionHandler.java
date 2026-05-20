package com.example.chatApp.exceptionHandler;

import com.example.chatApp.dtos.responses.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 409 CONFLICT — email already registered
    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleEmailAlreadyExists(
            EmailAlreadyExistsException ex, HttpServletRequest request) {

        log.warn("Registration conflict: {}", ex.getMessage());
        return build(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), request.getRequestURI(), null);
    }

    // 404 NOT FOUND — user does not exist
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(
            UserNotFoundException ex, HttpServletRequest request) {

        log.warn("User not found: {}", ex.getMessage());
        return build(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), request.getRequestURI(), null);
    }

    // 401 UNAUTHORIZED — wrong password
    @ExceptionHandler(InvalidPasswordException.class)
    public ResponseEntity<ErrorResponse> handleInvalidPassword(
            InvalidPasswordException ex, HttpServletRequest request) {

        log.warn("Invalid password attempt at {}", request.getRequestURI());
        return build(HttpStatus.UNAUTHORIZED, "Unauthorized", ex.getMessage(), request.getRequestURI(), null);
    }

    // 502 BAD GATEWAY — jwt-server unreachable or failed
    @ExceptionHandler(JwtServerException.class)
    public ResponseEntity<ErrorResponse> handleJwtServerException(
            JwtServerException ex, HttpServletRequest request) {

        log.error("JWT server error: {}", ex.getMessage());
        return build(HttpStatus.BAD_GATEWAY, "JWT Service Error", ex.getMessage(), request.getRequestURI(), null);
    }

    // 400 BAD REQUEST — @Valid annotation failures
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        List<String> details = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .toList();

        log.warn("Validation failed at {}: {}", request.getRequestURI(), details);
        return build(HttpStatus.BAD_REQUEST, "Validation Failed",
                "One or more fields are invalid", request.getRequestURI(), details);
    }

    // 400 BAD REQUEST — General Validation Failures (jakarta.validation.ValidationException)
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleGeneralValidationException(
            ValidationException ex, HttpServletRequest request) {

        log.warn("General validation error at {}: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "Validation Error",
                ex.getMessage(), request.getRequestURI(), null);
    }

    // 403 FORBIDDEN — user does not have permission
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbiddenException(
            ForbiddenException ex, HttpServletRequest request) {

        log.warn("Forbidden access at {}: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.FORBIDDEN, "Forbidden",
                ex.getMessage(), request.getRequestURI(), null);
    }

    // 502 BAD GATEWAY — weather server unreachable or failed
    @ExceptionHandler(WeatherServerException.class)
    public ResponseEntity<ErrorResponse> handleWeatherServerException(
            WeatherServerException ex, HttpServletRequest request) {

        log.error("Weather server error: {}", ex.getMessage());
        return build(HttpStatus.BAD_GATEWAY, "Weather Service Error",
                ex.getMessage(), request.getRequestURI(), null);
    }

    // 500 INTERNAL SERVER ERROR — anything unexpected
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex, HttpServletRequest request) {

        log.error("Unexpected error at {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                "An unexpected error occurred. Please try again later.", request.getRequestURI(), null);
    }

    // ── Helper ────────────────────────────────────────────────────────────────
    private ResponseEntity<ErrorResponse> build(HttpStatus status, String error,
                                                String message, String path,
                                                List<String> details) {
        ErrorResponse body = ErrorResponse.builder()
                .success(false)
                .status(status.value())
                .error(error)
                .message(message)
                .path(path)
                .timestamp(LocalDateTime.now())
                .details(details)
                .build();

        return ResponseEntity.status(status).body(body);
    }
}