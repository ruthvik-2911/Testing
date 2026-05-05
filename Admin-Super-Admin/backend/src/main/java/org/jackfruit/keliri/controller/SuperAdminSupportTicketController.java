package org.jackfruit.keliri.controller;

import org.jackfruit.keliri.model.Ticket;
import org.jackfruit.keliri.service.SupportTicketService;
import org.jackfruit.keliri.service.SuperAdminManagementService;
import org.jackfruit.keliri.service.JwtService;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/superadmin/tickets")
public class SuperAdminSupportTicketController {

    @Autowired
    private SupportTicketService ticketService;

    @Autowired
    private SuperAdminManagementService managementService;

    @Autowired
    private JwtService jwtService;

    private Claims getClaimsFromAuth(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer "))
            return null;
        try {
            String token = authHeader.substring(7);
            return jwtService.parseToken(token);
        } catch (Exception e) {
            return null;
        }
    }

    @GetMapping
    public ResponseEntity<?> getAllTickets(@RequestParam(required = false) String status,
            @RequestParam(required = false) String adminId,
            jakarta.servlet.http.HttpServletRequest request) {
        Claims claims = getClaimsFromAuth(request.getHeader("Authorization"));
        String actorName = claims != null ? claims.get("name", String.class) : "Super Admin";
        String actorRole = claims != null ? claims.get("role", String.class) : "Super Admin";

        managementService.recordAuditEvent(
                actorName,
                actorRole,
                "Ticket List View",
                "Ticket",
                adminId != null ? adminId : "ALL",
                "Viewed support tickets" + (adminId != null ? " for admin: " + adminId : ""),
                clientIp(request),
                "Local Ticket");
        return ResponseEntity.ok(Map.of("success", true, "tickets", ticketService.getAllTickets(status, adminId)));
    }

    @GetMapping("/{ticketId}")
    public ResponseEntity<?> getTicketDetail(@PathVariable String ticketId,
            jakarta.servlet.http.HttpServletRequest request) {
        Map<String, Object> detail = ticketService.getTicketDetail(ticketId);
        if (detail == null)
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", "Ticket not found"));
        Claims claims = getClaimsFromAuth(request.getHeader("Authorization"));
        String actorName = claims != null ? claims.get("name", String.class) : "Super Admin";
        String actorRole = claims != null ? claims.get("role", String.class) : "Super Admin";

        managementService.recordAuditEvent(
                actorName,
                actorRole,
                "Ticket Detail View",
                "Ticket",
                ticketId,
                "Viewed support ticket detail",
                clientIp(request),
                "Local Ticket");
        return ResponseEntity.ok(Map.of("success", true, "data", detail));
    }

    @PostMapping("/{ticketId}/reply")
    public ResponseEntity<?> replyTicket(@PathVariable String ticketId, @RequestBody Map<String, String> body,
            jakarta.servlet.http.HttpServletRequest request) {
        String message = body.get("message");
        if (message == null || message.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Message is required"));
        }

        Claims claims = getClaimsFromAuth(request.getHeader("Authorization"));
        String actorName = claims != null ? claims.get("name", String.class) : "Super Admin";
        String actorRole = claims != null ? claims.get("role", String.class) : "Super Admin";

        var reply = ticketService.replyToTicket(ticketId, "SUPER_ADMIN", message);
        managementService.recordAuditEvent(
                actorName,
                actorRole,
                "Ticket Reply",
                "Ticket",
                ticketId,
                "Replied to support ticket",
                clientIp(request),
                "Local Ticket");
        return ResponseEntity.ok(Map.of("success", true, "message", reply));
    }

    @PatchMapping("/{ticketId}/status")
    public ResponseEntity<?> updateStatus(@PathVariable String ticketId, @RequestBody Map<String, String> body,
            jakarta.servlet.http.HttpServletRequest request) {
        String newStatus = body.get("status");
        if (newStatus == null || newStatus.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Status is required"));
        }

        Claims claims = getClaimsFromAuth(request.getHeader("Authorization"));
        String actorName = claims != null ? claims.get("name", String.class) : "Super Admin";
        String actorRole = claims != null ? claims.get("role", String.class) : "Super Admin";

        Ticket ticket = ticketService.updateTicketStatus(ticketId, newStatus);
        managementService.recordAuditEvent(
                actorName,
                actorRole,
                "Ticket Status Update",
                "Ticket",
                ticketId,
                "Updated ticket status to " + newStatus.toUpperCase(),
                clientIp(request),
                "Local Ticket");
        return ResponseEntity.ok(Map.of("success", true, "ticket", ticket));
    }

    private String clientIp(jakarta.servlet.http.HttpServletRequest request) {
        if (request == null)
            return "unknown";
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
