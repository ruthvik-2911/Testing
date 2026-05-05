package org.jackfruit.keliri.controller;

import io.jsonwebtoken.Claims;
import org.jackfruit.keliri.model.ad_campaigns;
import org.jackfruit.keliri.repository.ad_campaignsRepository;
import org.jackfruit.keliri.service.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

/**
 * Controller to handle administrative grouping and scoping of
 * advertisements/campaigns
 * created via the Mobilize API into the local Admin account context.
 */
@RestController
@RequestMapping("/api/admin/ads")
public class AdminAdController {

    @Autowired
    private ad_campaignsRepository campaignsRepository;

    @Autowired
    private JwtService jwtService;

    /**
     * Scopes a freshly created campaign to the current authenticated admin.
     * This is used after the frontend successfully creates a campaign in the
     * Mobilize API (EC2).
     */
    @PostMapping("/scope")
    public ResponseEntity<?> scopeCampaign(@RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> payload) {
        try {
            String token = authHeader.substring(7);
            Claims claims = jwtService.parseToken(token);
            String adminId = claims.get("userId", String.class);

            String campaignId = payload.get("campaignId");
            String advertisementId = payload.get("advertisementId");

            if (campaignId != null) {
                // Find campaign in local collection (synced with same DB as Mobilize API)
                Optional<ad_campaigns> campaignOpt = campaignsRepository.findById(campaignId);
                if (campaignOpt.isPresent()) {
                    ad_campaigns campaign = campaignOpt.get();
                    campaign.setCreatedBy(adminId);
                    campaignsRepository.save(campaign);
                    System.out.println("====== SCOPED CAMPAIGN " + campaignId + " TO ADMIN " + adminId + " ======");
                    return ResponseEntity.ok(Map.of("success", true, "message", "Campaign scoped successfully"));
                }
            }

            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Campaign not found or missing IDs"));

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", e.getMessage()));
        }
    }
}
