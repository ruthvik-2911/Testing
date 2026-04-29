package org.jackfruit.keliri.controller;

import org.jackfruit.keliri.model.SuperAdminAnalyticsResponse;
import org.jackfruit.keliri.model.SuperAdminRevenueResponse;
import org.jackfruit.keliri.service.SuperAdminAnalyticsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/superadmin/analytics")
public class SuperAdminAnalyticsController {
    private final SuperAdminAnalyticsService analyticsService;

    public SuperAdminAnalyticsController(SuperAdminAnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping
    public ResponseEntity<SuperAdminAnalyticsResponse> getAnalytics() {
        System.out.println(">>> RECEIVED REQUEST: /api/superadmin/analytics");
        SuperAdminAnalyticsResponse response = analyticsService.getAnalytics();
        System.out.println("<<< RESPONDING WITH: " + response.getKpis().size() + " KPI cards");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/revenue")
    public ResponseEntity<SuperAdminRevenueResponse> getRevenueAnalytics() {
        return ResponseEntity.ok(analyticsService.getRevenueAnalytics());
    }
}
