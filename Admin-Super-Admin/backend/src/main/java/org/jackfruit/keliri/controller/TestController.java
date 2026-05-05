package org.jackfruit.keliri.controller;

import org.jackfruit.keliri.service.MobilizeApiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/test")
public class TestController {

    @Autowired
    private MobilizeApiService mobilizeApiService;

    @GetMapping("/mobilize-publishers")
    public ResponseEntity<?> testMobilizePublishers() {
        try {
            List<Map<String, Object>> publishers = mobilizeApiService.fetchPublishersFromProduction();
            return ResponseEntity.ok(Map.of(
                "success", true,
                "count", publishers.size(),
                "data", publishers
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    @GetMapping("/mobilize-campaigns")
    public ResponseEntity<?> testMobilizeCampaigns() {
        try {
            List<Map<String, Object>> campaigns = mobilizeApiService.fetchCampaignsFromProduction();
            return ResponseEntity.ok(Map.of(
                "success", true,
                "count", campaigns.size(),
                "data", campaigns
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
}
