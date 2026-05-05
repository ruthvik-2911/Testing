package org.jackfruit.keliri.controller;

import org.jackfruit.keliri.service.FirebasePhoneOTPService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth/phone")
public class PhoneOTPController {

    @Autowired
    private org.jackfruit.keliri.service.OtpService otpService;

    /**
     * Send OTP to phone number
     * POST /api/auth/phone/send-otp
     */
    @PostMapping("/send-otp")
    public ResponseEntity<?> sendOTP(@RequestBody Map<String, String> request) {
        try {
            String phoneNumber = request.get("phoneNumber");

            if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", "Phone number is required"));
            }

            // Format phone number (add +91 if missing for Indian numbers)
            if (!phoneNumber.startsWith("+")) {
                if (phoneNumber.length() == 10) {
                    phoneNumber = "+91" + phoneNumber;
                } else if (phoneNumber.length() == 12 && phoneNumber.startsWith("91")) {
                    phoneNumber = "+" + phoneNumber;
                }
            }

            String sessionId = otpService.initiateOtp(phoneNumber);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "OTP sent successfully via AWS SNS",
                    "sessionId", sessionId,
                    "phoneNumber", phoneNumber,
                    "expiresIn", 300));

        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("success", false, "message", "Failed to send OTP: " + e.getMessage()));
        }
    }

    /**
     * Verify OTP and login
     * POST /api/auth/phone/verify-otp
     */
    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOTP(@RequestBody Map<String, String> request) {
        try {
            String sessionId = request.get("sessionId");
            String otp = request.get("otp");
            String phoneNumber = request.get("phoneNumber");

            if (sessionId == null || sessionId.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", "Session ID is required"));
            }

            if (otp == null || otp.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", "OTP is required"));
            }

            if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", "Phone number is required"));
            }

            boolean isValid = otpService.verifyOtp(sessionId, otp);

            if (isValid) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "OTP verified successfully",
                        "sessionId", sessionId));
            } else {
                return ResponseEntity.status(401).body(Map.of(
                        "success", false,
                        "message", "Invalid or expired OTP"));
            }

        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("success", false, "message", "OTP verification failed: " + e.getMessage()));
        }
    }

    /**
     * Test OTP service
     * GET /api/auth/phone/test
     */
    @GetMapping("/test")
    public ResponseEntity<?> testOtpService() {
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "AWS Phone OTP service is active"));
    }
}
