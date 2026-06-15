package com.hospitalfamilia.server.common.exception;

import com.hospitalfamilia.server.auth.exception.AuthException;
import com.hospitalfamilia.server.beta.exception.BetaExitChecklistException;
import com.hospitalfamilia.server.auth.exception.UserAlreadyExistsException;
import com.hospitalfamilia.server.common.dto.ApiResponse;
import com.hospitalfamilia.server.contact.exception.ContactRequestException;
import com.hospitalfamilia.server.events.exception.EventException;
import com.hospitalfamilia.server.linking.exception.LinkingException;
import com.hospitalfamilia.server.notifications.exception.NotificationPreferenceException;
import com.hospitalfamilia.server.patientstatus.exception.PatientStatusException;
import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AuthException.class)
    ResponseEntity<ApiResponse<Void>> handleAuthException(AuthException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(ex.getMessage(), null));
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    ResponseEntity<ApiResponse<Void>> handleUserAlreadyExists(UserAlreadyExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(ex.getMessage(), null));
    }

    @ExceptionHandler(LinkingException.class)
    ResponseEntity<ApiResponse<Void>> handleLinkingException(LinkingException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage(), null));
    }

    @ExceptionHandler(PatientStatusException.class)
    ResponseEntity<ApiResponse<Void>> handlePatientStatusException(PatientStatusException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(ex.getMessage(), null));
    }

    @ExceptionHandler(EventException.class)
    ResponseEntity<ApiResponse<Void>> handleEventException(EventException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage(), null));
    }

    @ExceptionHandler(NotificationPreferenceException.class)
    ResponseEntity<ApiResponse<Void>> handleNotificationPreferenceException(NotificationPreferenceException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage(), null));
    }

    @ExceptionHandler(ContactRequestException.class)
    ResponseEntity<ApiResponse<Void>> handleContactRequestException(ContactRequestException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage(), null));
    }

    @ExceptionHandler(BetaExitChecklistException.class)
    ResponseEntity<ApiResponse<Void>> handleBetaExitChecklistException(BetaExitChecklistException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage(), null));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Acceso denegado", null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(ApiResponse.error(message, null));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage(), null));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error("Error interno del servidor", null));
    }
}
