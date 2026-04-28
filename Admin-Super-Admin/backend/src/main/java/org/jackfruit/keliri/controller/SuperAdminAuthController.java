package org.jackfruit.keliri.controller;

import java.util.Map;

import org.jackfruit.keliri.model.LoginRequest;
import org.jackfruit.keliri.service.SuperAdminAuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth/superadmin")
public class SuperAdminAuthController {

    @Autowired
    private SuperAdminAuthService authService;

    /**
     * Super Admin Login - Email & Password
     * Generates JWT token valid for 24 hours
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest credentials) {
        try {
            if (credentials.getEmail() == null || credentials.getEmail().isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Email is required", "error", "INVALID_REQUEST"));
            }
            if (credentials.getPassword() == null || credentials.getPassword().isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Password is required", "error", "INVALID_REQUEST"));
            }

            Map<String, Object> payload = authService.login(credentials.getEmail(), credentials.getPassword());
            return ResponseEntity.ok(payload);
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode())
                    .body(Map.of("message", e.getReason(), "error", e.getStatusCode().toString()));
        } catch (Exception e) {
            return ResponseEntity.status(401)
                    .body(Map.of("message", e.getMessage(), "error", "LOGIN_FAILED"));
        }
    }

    /**
     * Get current session info
     * Validates and refreshes token
     */
    @GetMapping("/me")
    public ResponseEntity<?> currentSession(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        try {
            return ResponseEntity.ok(authService.getCurrentSession(authorization));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode())
                    .body(Map.of("message", e.getReason(), "error", e.getStatusCode().toString()));
        } catch (Exception e) {
            return ResponseEntity.status(401)
                    .body(Map.of("message", e.getMessage(), "error", "SESSION_ERROR"));
        }
    }

    /**
     * Validate JWT Token
     * Returns token validity status and remaining time
     */
    @PostMapping("/validate-token")
    public ResponseEntity<?> validateToken(@RequestBody Map<String, String> request) {
        try {
            String token = request.get("token");
            if (token == null || token.isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("valid", false, "message", "Token is required"));
            }

            Map<String, Object> validationResult = authService.validateToken(token);
            return ResponseEntity.ok(validationResult);
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("valid", false, "message", "Token validation failed", "error", e.getMessage()));
        }
    }

    /**
     * Unlock Account using token
     */
    @PostMapping("/unlock-account")
    public ResponseEntity<?> unlockAccount(@RequestBody Map<String, String> request) {
        try {
            String token = request.get("token");
            return ResponseEntity.ok(authService.unlockAccountByToken(token));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode())
                    .body(Map.of("message", e.getReason(), "error", e.getStatusCode().toString()));
        } catch (Exception e) {
            return ResponseEntity.status(400)
                    .body(Map.of("message", e.getMessage(), "error", "UNLOCK_FAILED"));
        }
    }

    /**
     * Logout
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        return ResponseEntity.ok(Map.of("message", "Logout Successful. Clear token and session on client."));
    }

    /**
     * Super Admin Login - Phone OTP (Firebase)
     *
     * Flow:
     * 1. Frontend uses Firebase JS SDK to send OTP to the phone number.
     * 2. User enters OTP → Firebase returns an ID token.
     * 3. Frontend sends that ID token here.
     * 4. Backend verifies the token with Firebase Admin SDK, extracts the phone
     * number,
     * finds the matching SuperAdmin, and returns a session JWT.
     *
     * POST /api/auth/superadmin/login-phone-otp
     * Body: { "firebaseIdToken": "<token from Firebase signInWithPhoneNumber>" }
     */
    @PostMapping("/login-phone-otp")
    public ResponseEntity<?> loginWithPhoneOtp(@RequestBody Map<String, String> request) {
        try {
            String firebaseIdToken = request.get("firebaseIdToken");
            if (firebaseIdToken == null || firebaseIdToken.isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "firebaseIdToken is required", "error", "INVALID_REQUEST"));
            }
            Map<String, Object> payload = authService.loginWithFirebaseOtp(firebaseIdToken);
            return ResponseEntity.ok(payload);
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode())
                    .body(Map.of("message", e.getReason(), "error", e.getStatusCode().toString()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", e.getMessage(), "error", "OTP_LOGIN_FAILED"));
        }
    }
}
