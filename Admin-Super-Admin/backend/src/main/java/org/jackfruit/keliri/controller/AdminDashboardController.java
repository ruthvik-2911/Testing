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

    @Autowired
    private AdminDashboardService dashboardService;

    @Autowired
    private JwtService jwtService;

    @GetMapping
    public ResponseEntity<?> getDashboard(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("success", false, "message", "Missing or invalid Authorization header"));
            }

            String token = authHeader.substring(7);
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

    @Autowired
    private MobilizeApiService mobilizeApiService;

    @GetMapping("/advertisements")
    public ResponseEntity<?> getAdvertisements(
            @RequestParam(required = false) String companyUID,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "200") int limit) {
        try {
            List<Map<String, Object>> advertisements = mobilizeApiService.fetchAdvertisements(companyUID, page, limit);
            return ResponseEntity.ok(Map.of("success", true, "data", advertisements, "count", advertisements.size()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Error fetching advertisements: " + e.getMessage()));
        }
    }

    @GetMapping("/dashboard-counts")
    public ResponseEntity<?> getDashboardCounts(@RequestParam(required = false) String companyUID) {
        try {
            Map<String, Object> counts = mobilizeApiService.fetchDashboardCounts(companyUID);
            return ResponseEntity.ok(Map.of("success", true, "data", counts));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Error fetching dashboard counts: " + e.getMessage()));
        }
    }
}
