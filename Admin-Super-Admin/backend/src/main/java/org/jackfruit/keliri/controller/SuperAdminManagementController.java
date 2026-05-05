package org.jackfruit.keliri.controller;

import java.util.List;

import org.jackfruit.keliri.model.SuperAdminManagementResponse;
import org.jackfruit.keliri.service.SuperAdminManagementService;
import org.jackfruit.keliri.service.JwtService;
import io.jsonwebtoken.Claims;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/superadmin")
public class SuperAdminManagementController {
    private final SuperAdminManagementService managementService;
    private final JwtService jwtService;

    public SuperAdminManagementController(SuperAdminManagementService managementService, JwtService jwtService) {
        this.managementService = managementService;
        this.jwtService = jwtService;
    }

    private Claims getClaims(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer "))
            return null;
        try {
            return jwtService.parseToken(auth.substring(7));
        } catch (Exception e) {
            return null;
        }
    }

    private String getActorName(HttpServletRequest request) {
        Claims c = getClaims(request);
        return c != null ? c.get("name", String.class) : "Super Admin";
    }

    private String getActorRole(HttpServletRequest request) {
        Claims c = getClaims(request);
        return c != null ? c.get("role", String.class) : "Super Admin";
    }

    @GetMapping("/admins")
    public ResponseEntity<List<SuperAdminManagementResponse.AdminRecord>> getAdmins(
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "status", required = false) String status) {
        return ResponseEntity.ok(managementService.getAdmins(search, status));
    }

    @GetMapping("/admins/{adminId}")
    public ResponseEntity<SuperAdminManagementResponse.AdminDetail> getAdminDetail(@PathVariable String adminId) {
        return ResponseEntity.ok(managementService.getAdminDetail(adminId));
    }

    @PostMapping("/admins/{adminId}/approve")
    public ResponseEntity<SuperAdminManagementResponse.AdminActionResponse> approveAdmin(@PathVariable String adminId,
            HttpServletRequest request) {
        return ResponseEntity.ok(managementService.approveAdmin(adminId, getActorName(request), getActorRole(request)));
    }

    @PostMapping("/admins/{adminId}/reject")
    public ResponseEntity<SuperAdminManagementResponse.AdminActionResponse> rejectAdmin(
            @PathVariable String adminId,
            @RequestBody(required = false) SuperAdminManagementResponse.AdminActionRequest requestBody,
            HttpServletRequest request) {
        String reason = requestBody != null ? requestBody.getReason() : null;
        return ResponseEntity
                .ok(managementService.rejectAdmin(adminId, reason, getActorName(request), getActorRole(request)));
    }

    @PostMapping("/admins/{adminId}/suspend")
    public ResponseEntity<SuperAdminManagementResponse.AdminActionResponse> suspendAdmin(@PathVariable String adminId,
            HttpServletRequest request) {
        return ResponseEntity.ok(managementService.suspendAdmin(adminId, getActorName(request), getActorRole(request)));
    }

    @PostMapping("/admins/{adminId}/reinstate")
    public ResponseEntity<SuperAdminManagementResponse.AdminActionResponse> reinstateAdmin(
            @PathVariable String adminId, HttpServletRequest request) {
        return ResponseEntity
                .ok(managementService.reinstateAdmin(adminId, getActorName(request), getActorRole(request)));
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/admins/{adminId}")
    public ResponseEntity<Void> deleteAdmin(@PathVariable String adminId, HttpServletRequest request) {
        managementService.deleteAdmin(adminId, getActorName(request), getActorRole(request));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/admin-notifications")
    public ResponseEntity<List<SuperAdminManagementResponse.EmailNotificationRecord>> getEmailNotifications() {
        return ResponseEntity.ok(managementService.getEmailNotifications());
    }

    @GetMapping("/publishers")
    public ResponseEntity<List<SuperAdminManagementResponse.PublisherRecord>> getPublishers(
            @RequestParam(value = "adminId", required = false) String adminId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "location", required = false) String location,
            @RequestParam(value = "search", required = false) String search) {
        return ResponseEntity.ok(managementService.getPublishers(adminId, status, location, search));
    }

    @GetMapping("/publishers/{publisherId}")
    public ResponseEntity<SuperAdminManagementResponse.PublisherDetail> getPublisherDetail(
            @PathVariable String publisherId) {
        return ResponseEntity.ok(managementService.getPublisherDetail(publisherId));
    }

    @GetMapping("/ads")
    public ResponseEntity<List<SuperAdminManagementResponse.AdvertisementRecord>> getAdvertisements() {
        return ResponseEntity.ok(managementService.getAdvertisements());
    }

    @PostMapping("/ads/{campaignId}/suspend")
    public ResponseEntity<SuperAdminManagementResponse.AdvertisementRecord> suspendAdvertisement(
            @PathVariable String campaignId, HttpServletRequest request) {
        return ResponseEntity
                .ok(managementService.suspendAdvertisement(campaignId, getActorName(request), getActorRole(request)));
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<List<SuperAdminManagementResponse.AuditLogRecord>> getAuditLogs(
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "actionType", required = false) String actionType,
            @RequestParam(value = "actorRole", required = false) String actorRole,
            @RequestParam(value = "entityType", required = false) String entityType,
            @RequestParam(value = "fromDate", required = false) String fromDate,
            @RequestParam(value = "toDate", required = false) String toDate) {
        return ResponseEntity
                .ok(managementService.getAuditLogs(search, actionType, actorRole, entityType, fromDate, toDate));
    }

    @GetMapping("/payments")
    public ResponseEntity<List<SuperAdminManagementResponse.TransactionRecord>> getTransactions(
            HttpServletRequest request) {
        return ResponseEntity.ok(managementService.getTransactions(getActorName(request), getActorRole(request)));
    }
}
