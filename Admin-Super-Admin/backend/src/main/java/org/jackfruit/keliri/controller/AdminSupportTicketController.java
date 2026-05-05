package org.jackfruit.keliri.controller;

import io.jsonwebtoken.Claims;
import org.jackfruit.keliri.model.Ticket;
import org.jackfruit.keliri.service.JwtService;
import org.jackfruit.keliri.service.SupportTicketService;
import org.jackfruit.keliri.service.SuperAdminManagementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/tickets")
public class AdminSupportTicketController {
    @Autowired
    private SupportTicketService ticketService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private SuperAdminManagementService managementService;

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

    private String getAdminIdFromAuth(String authHeader) {
        Claims claims = getClaimsFromAuth(authHeader);
        return claims != null ? claims.get("userId", String.class) : null;
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<?> createTicket(
            @RequestHeader(value = "Authorization", required = false) String auth,
            jakarta.servlet.http.HttpServletRequest request,
            @RequestParam("subject") String subject,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam("message") String message,
            @RequestParam(value = "attachments", required = false) MultipartFile[] attachments) {
        String adminId = getAdminIdFromAuth(auth);
        if (adminId == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "Unauthorized"));

        if (subject == null || subject.trim().isEmpty() || message == null || message.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Subject and message are required"));
        }

        Claims claims = getClaimsFromAuth(auth);
        String actorName = claims != null ? claims.get("name", String.class) : "Admin";
        String actorRole = claims != null ? claims.get("role", String.class) : "Admin";

        List<MultipartFile> attachmentList = attachments == null ? Collections.emptyList() : Arrays.asList(attachments);
        Ticket ticket = ticketService.createTicket(adminId, subject, category, message, attachmentList);
        managementService.recordAuditEvent(
                actorName,
                actorRole,
                "Ticket Created",
                "Ticket",
                ticket.getId(),
                "Created support ticket: " + subject,
                clientIp(request),
                "Local Ticket");
        return ResponseEntity.ok(Map.of("success", true, "ticket", ticket));
    }

    @GetMapping
    public ResponseEntity<?> getTickets(@RequestHeader(value = "Authorization", required = false) String auth,
            jakarta.servlet.http.HttpServletRequest request) {
        String adminId = getAdminIdFromAuth(auth);
        if (adminId == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "Unauthorized"));

        Claims claims = getClaimsFromAuth(auth);
        String actorName = claims != null ? claims.get("name", String.class) : "Admin";
        String actorRole = claims != null ? claims.get("role", String.class) : "Admin";

        managementService.recordAuditEvent(actorName, actorRole, "Ticket List View", "Ticket", adminId,
                "Viewed ticket list", clientIp(request), "Local Ticket");
        return ResponseEntity.ok(Map.of("success", true, "tickets", ticketService.getTicketsForAdmin(adminId)));
    }

    @GetMapping("/{ticketId}")
    public ResponseEntity<?> getTicketDetail(@RequestHeader(value = "Authorization", required = false) String auth,
            jakarta.servlet.http.HttpServletRequest request,
            @PathVariable String ticketId) {
        String adminId = getAdminIdFromAuth(auth);
        if (adminId == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "Unauthorized"));

        Map<String, Object> detail = ticketService.getTicketDetail(ticketId);
        if (detail == null)
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", "Ticket not found"));

        Ticket t = (Ticket) detail.get("ticket");
        if (!t.getAdminId().equals(adminId))
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("success", false, "message", "Forbidden"));

        Claims claims = getClaimsFromAuth(auth);
        String actorName = claims != null ? claims.get("name", String.class) : "Admin";
        String actorRole = claims != null ? claims.get("role", String.class) : "Admin";

        managementService.recordAuditEvent(actorName, actorRole, "Ticket Detail View", "Ticket", ticketId,
                "Viewed ticket detail", clientIp(request), "Local Ticket");
        return ResponseEntity.ok(Map.of("success", true, "data", detail));
    }

    @PostMapping("/{ticketId}/reply")
    public ResponseEntity<?> replyTicket(@RequestHeader(value = "Authorization", required = false) String auth,
            jakarta.servlet.http.HttpServletRequest request,
            @PathVariable String ticketId, @RequestBody Map<String, String> body) {
        String adminId = getAdminIdFromAuth(auth);
        if (adminId == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "Unauthorized"));

        Map<String, Object> detail = ticketService.getTicketDetail(ticketId);
        if (detail == null)
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", "Ticket not found"));

        Ticket t = (Ticket) detail.get("ticket");
        if (!t.getAdminId().equals(adminId))
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("success", false, "message", "Forbidden"));

        String message = body.get("message");
        if (message == null || message.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Message is required"));
        }

        Claims claims = getClaimsFromAuth(auth);
        String actorName = claims != null ? claims.get("name", String.class) : "Admin";
        String actorRole = claims != null ? claims.get("role", String.class) : "Admin";

        var reply = ticketService.replyToTicket(ticketId, "ADMIN", message);
        managementService.recordAuditEvent(actorName, actorRole, "Ticket Reply", "Ticket", ticketId,
                "Admin replied to support ticket", clientIp(request), "Local Ticket");
        return ResponseEntity.ok(Map.of("success", true, "message", reply));
    }

    @PatchMapping("/{ticketId}/reopen")
    public ResponseEntity<?> reopenTicket(@RequestHeader(value = "Authorization", required = false) String auth,
            jakarta.servlet.http.HttpServletRequest request,
            @PathVariable String ticketId) {
        String adminId = getAdminIdFromAuth(auth);
        if (adminId == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "Unauthorized"));

        Map<String, Object> detail = ticketService.getTicketDetail(ticketId);
        if (detail == null)
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", "Ticket not found"));

        Ticket t = (Ticket) detail.get("ticket");
        if (!t.getAdminId().equals(adminId))
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("success", false, "message", "Forbidden"));

        Claims claims = getClaimsFromAuth(auth);
        String actorName = claims != null ? claims.get("name", String.class) : "Admin";
        String actorRole = claims != null ? claims.get("role", String.class) : "Admin";

        Ticket ticket = ticketService.updateTicketStatus(ticketId, "OPEN");
        managementService.recordAuditEvent(actorName, actorRole, "Ticket Reopen", "Ticket", ticketId,
                "Reopened support ticket", clientIp(request), "Local Ticket");
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
