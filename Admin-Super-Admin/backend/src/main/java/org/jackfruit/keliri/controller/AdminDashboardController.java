package org.jackfruit.keliri.controller;

import io.jsonwebtoken.Claims;
import org.jackfruit.keliri.service.AdminDashboardService;
import org.jackfruit.keliri.service.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardController {

    public AdminDashboardController() {
        System.out.println("====== AdminDashboardController Initialized ======");
    }

    @Autowired
    private AdminDashboardService dashboardService;

    @Autowired
    private JwtService jwtService;

    @GetMapping
    public ResponseEntity<?> getDashboard(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                System.out.println("DEBUG: Missing or invalid Auth Header: " + authHeader);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("success", false, "message", "Missing or invalid Authorization header"));
            }

            String token = authHeader.substring(7);
            System.out.println("DEBUG: Token received: [" + token + "]");
            Claims claims = jwtService.parseToken(token);
            String adminId = claims.get("userId", String.class);

            if (adminId == null || adminId.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("success", false, "message", "Invalid token: missing userId"));
            }

            return ResponseEntity.ok(dashboardService.getDashboardData(adminId));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Internal server error: " + e.getMessage()));
        }
    }
}
