package org.jackfruit.keliri.service;

import org.jackfruit.keliri.model.Ticket;
import org.jackfruit.keliri.model.TicketMessage;
import org.jackfruit.keliri.repository.TicketMessageRepository;
import org.jackfruit.keliri.repository.TicketRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class SupportTicketService {

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private TicketMessageRepository messageRepository;

    @Autowired
    private FileStorageService fileStorageService;

    public Ticket createTicket(String adminId, String subject, String category, String initialMessage, List<MultipartFile> attachments) {
        Ticket ticket = new Ticket();
        ticket.setAdminId(adminId);
        ticket.setSubject(subject);
        ticket.setCategory(category != null ? category : "OTHER");
        ticket.setStatus("OPEN");
        ticket.setCreatedAt(Instant.now());
        ticket.setUpdatedAt(Instant.now());

        Ticket savedTicket = ticketRepository.save(ticket);

        TicketMessage message = new TicketMessage();
        message.setTicketId(savedTicket.getId());
        message.setSenderType("ADMIN");
        message.setMessage(initialMessage);
        message.setCreatedAt(Instant.now());
        List<String> attachmentUrls = uploadAttachments(adminId, savedTicket.getId(), attachments);
        message.setAttachmentUrls(attachmentUrls);
        if (!attachmentUrls.isEmpty()) {
            message.setAttachmentUrl(attachmentUrls.get(0));
        }

        messageRepository.save(message);

        // TODO: email notifications placeholder
        // emailService.sendTicketCreatedEmail(adminId, savedTicket);

        return savedTicket;
    }

    public List<Ticket> getTicketsForAdmin(String adminId) {
        return ticketRepository.findByAdminIdOrderByUpdatedAtDesc(adminId);
    }

    public List<Ticket> getAllTickets(String statusFilter, String adminIdFilter) {
        List<Ticket> allTickets = ticketRepository.findAll();
        // Since there could be a lot, in a production app we'd query exactly, but standard list filtering fits our immediate scope
        return allTickets.stream()
                .filter(t -> (statusFilter == null || statusFilter.equalsIgnoreCase(t.getStatus())))
                .filter(t -> (adminIdFilter == null || adminIdFilter.equals(t.getAdminId())))
                .sorted((a, b) -> b.getUpdatedAt().compareTo(a.getUpdatedAt()))
                .toList();
    }

    public Map<String, Object> getTicketDetail(String ticketId) {
        Optional<Ticket> ticketOpt = ticketRepository.findById(ticketId);
        if (ticketOpt.isEmpty()) {
            return null;
        }

        Ticket ticket = ticketOpt.get();
        List<TicketMessage> messages = messageRepository.findByTicketIdOrderByCreatedAtAsc(ticketId);

        Map<String, Object> response = new HashMap<>();
        response.put("ticket", ticket);
        response.put("messages", messages);
        return response;
    }

    public TicketMessage replyToTicket(String ticketId, String senderType, String messageContent) {
        Optional<Ticket> ticketOpt = ticketRepository.findById(ticketId);
        if (ticketOpt.isEmpty()) {
            throw new RuntimeException("Ticket not found");
        }

        Ticket ticket = ticketOpt.get();

        TicketMessage message = new TicketMessage();
        message.setTicketId(ticketId);
        message.setSenderType(senderType);
        message.setMessage(messageContent);
        message.setCreatedAt(Instant.now());

        messageRepository.save(message);

        // Update ticket's updatedAt
        ticket.setUpdatedAt(Instant.now());
        
        // Auto-update status if super admin replies and it's OPEN
        if ("SUPER_ADMIN".equals(senderType) && "OPEN".equals(ticket.getStatus())) {
            ticket.setStatus("IN_PROGRESS");
        }

        ticketRepository.save(ticket);

        // TODO: email notifications placeholder
        // emailService.sendTicketReplyEmail(ticket, message);

        return message;
    }

    public Ticket updateTicketStatus(String ticketId, String newStatus) {
        Optional<Ticket> ticketOpt = ticketRepository.findById(ticketId);
        if (ticketOpt.isEmpty()) {
            throw new RuntimeException("Ticket not found");
        }

        Ticket ticket = ticketOpt.get();
        ticket.setStatus(newStatus.toUpperCase());
        ticket.setUpdatedAt(Instant.now());
        ticketRepository.save(ticket);

        // TODO: email notifications placeholder
        // emailService.sendTicketStatusUpdateEmail(ticket);

        return ticket;
    }

    private List<String> uploadAttachments(String adminId, String ticketId, List<MultipartFile> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> uploadedUrls = new ArrayList<>();
        String basePath = "tickets/" + adminId + "/" + ticketId;
        for (int i = 0; i < attachments.size(); i++) {
            MultipartFile attachment = attachments.get(i);
            if (attachment == null || attachment.isEmpty()) {
                continue;
            }
            try {
                uploadedUrls.add(fileStorageService.uploadFile(attachment, basePath + "/attachment-" + (i + 1)));
            } catch (IOException e) {
                throw new RuntimeException("Failed to upload ticket attachment", e);
            }
        }
        return uploadedUrls;
    }
}
