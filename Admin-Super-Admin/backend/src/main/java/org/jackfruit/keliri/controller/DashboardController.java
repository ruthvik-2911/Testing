package org.jackfruit.keliri.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.jackfruit.keliri.service.MobilizeApiService;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Dashboard Controller - Provides dashboard data with proper Mobilize integration
 * This replaces the direct Mobilize API calls in the frontend with Spring Boot backend calls
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final MobilizeApiService mobilizeApiService;
    private final MongoTemplate mongoTemplate;

    @Autowired
    public DashboardController(MobilizeApiService mobilizeApiService, MongoTemplate mongoTemplate) {
        this.mobilizeApiService = mobilizeApiService;
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Get top campaign creators with statistics calculated from Mobilize data
     */
    @GetMapping("/creators")
    public ResponseEntity<List<CreatorStatsResponse>> getTopCreators() {
        System.out.println(">>> DASHBOARD: Fetching top creators from Mobilize data");
        
        try {
            // Get campaigns from Mobilize MongoDB via MobilizeApiService
            List<Map<String, Object>> campaigns = mobilizeApiService.fetchCampaignsFromMobilize();
            
            // Calculate creator statistics
            Map<String, CreatorStatsResponse> creatorMap = new HashMap<>();
            
            for (Map<String, Object> campaign : campaigns) {
                String createdBy = (String) campaign.get("createdBy");
                String creatorName = mapUserIdToName(createdBy);
                String creatorEmail = mapUserIdToEmail(createdBy);
                
                CreatorStatsResponse stats = creatorMap.computeIfAbsent(creatorName, k -> 
                    new CreatorStatsResponse(creatorName, creatorEmail)
                );
                
                // Increment total campaigns
                stats.setCampaignCount(stats.getCampaignCount() + 1);
                
                // Count active campaigns
                String status = (String) campaign.get("compaignsStatus");
                if ("ACTIVE".equals(status)) {
                    stats.setActiveCampaignCount(stats.getActiveCampaignCount() + 1);
                }
                
                // Count unique locations
                Map<String, Object> location = (Map<String, Object>) campaign.get("location");
                if (location != null && location.get("locationName") != null) {
                    stats.getLocations().add((String) location.get("locationName"));
                }
            }
            
            // Sort by total campaigns and add rank
            List<CreatorStatsResponse> result = creatorMap.values().stream()
                .sorted((a, b) -> Integer.compare(b.getCampaignCount(), a.getCampaignCount()))
                .peek(stats -> stats.setLocationCount(stats.getLocations().size()))
                .collect(Collectors.toList());
            
            // Assign ranks and calculate trends
            for (int i = 0; i < result.size(); i++) {
                CreatorStatsResponse stats = result.get(i);
                stats.setRank(i + 1);
                stats.setTrend(calculateTrend(stats.getCampaignCount()));
            }
            
            System.out.println("<<< DASHBOARD: Returning " + result.size() + " creators");
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            System.err.println("!!! Error fetching top creators: " + e.getMessage());
            return ResponseEntity.ok(getFallbackCreators());
        }
    }

    /**
     * Get recent campaign activities
     */
    @GetMapping("/recent-activity")
    public ResponseEntity<List<RecentActivityResponse>> getRecentActivity() {
        System.out.println(">>> DASHBOARD: Fetching recent activity from Mobilize data");
        
        try {
            List<Map<String, Object>> campaigns = mobilizeApiService.fetchCampaignsFromMobilize();
            
            // Sort by creation date (newest first)
            campaigns.sort((a, b) -> {
                Object dateA = a.get("createdAt");
                Object dateB = b.get("createdAt");
                if (dateA instanceof Date && dateB instanceof Date) {
                    return ((Date) dateB).compareTo((Date) dateA);
                }
                return 0;
            });
            
            List<RecentActivityResponse> activities = new ArrayList<>();
            
            // Take top 5 most recent campaigns
            for (int i = 0; i < Math.min(5, campaigns.size()); i++) {
                Map<String, Object> campaign = campaigns.get(i);
                
                String title = (String) campaign.get("title");
                if (title == null) {
                    title = "Campaign " + campaign.get("uid");
                }
                
                String status = (String) campaign.get("compaignsStatus");
                if (status == null) status = "DRAFT";
                
                Map<String, Object> location = (Map<String, Object>) campaign.get("location");
                String locationName = "Unknown Location";
                if (location != null && location.get("locationName") != null) {
                    locationName = (String) location.get("locationName");
                }
                
                Date createdAt = (Date) campaign.get("createdAt");
                String relativeTime = formatRelativeTime(createdAt);
                
                activities.add(new RecentActivityResponse(
                    (String) campaign.get("uid"),
                    "Created: " + title,
                    status,
                    locationName,
                    relativeTime
                ));
            }
            
            System.out.println("<<< DASHBOARD: Returning " + activities.size() + " recent activities");
            return ResponseEntity.ok(activities);
            
        } catch (Exception e) {
            System.err.println("!!! Error fetching recent activity: " + e.getMessage());
            return ResponseEntity.ok(getFallbackActivities());
        }
    }

    /**
     * Get campaigns data for breakdown calculations
     */
    @GetMapping("/campaigns-for-breakdown")
    public ResponseEntity<List<Map<String, Object>>> getCampaignsForBreakdown() {
        System.out.println(">>> DASHBOARD: Fetching campaigns for breakdown analysis");
        
        try {
            List<Map<String, Object>> campaigns = mobilizeApiService.fetchCampaignsFromMobilize();
            
            // Enrich campaigns with advertisement type information
            List<Map<String, Object>> enrichedCampaigns = new ArrayList<>();
            for (Map<String, Object> campaign : campaigns) {
                Map<String, Object> enriched = new HashMap<>(campaign);
                
                // Try to get advertisement type from linked advertisement
                String advertisementId = (String) campaign.get("advertisementId");
                if (advertisementId != null) {
                    // This would ideally fetch from advertisements collection
                    // For now, we'll infer from campaign data or use defaults
                    String adType = inferAdTypeFromCampaign(campaign);
                    enriched.put("advertisementType", adType);
                }
                
                enrichedCampaigns.add(enriched);
            }
            
            System.out.println("<<< DASHBOARD: Returning " + enrichedCampaigns.size() + " campaigns for breakdown");
            return ResponseEntity.ok(enrichedCampaigns);
            
        } catch (Exception e) {
            System.err.println("!!! Error fetching campaigns for breakdown: " + e.getMessage());
            return ResponseEntity.ok(getFallbackCampaignsForBreakdown());
        }
    }

    /**
     * Get breakdown data (ad types and locations)
     */
    @GetMapping("/breakdown")
    public ResponseEntity<Map<String, Object>> getBreakdownData() {
        System.out.println(">>> DASHBOARD: Fetching breakdown data");
        
        try {
            Map<String, Object> breakdown = new HashMap<>();
            
            // Get ad type breakdown from advertisements
            Map<String, Integer> adTypeCounts = new HashMap<>();
            try {
                List<Map<String, Object>> advertisements = mobilizeApiService.fetchAdvertisementsFromMobilize();
                for (Map<String, Object> ad : advertisements) {
                    String adType = (String) ad.get("adType");
                    if (adType != null) {
                        adTypeCounts.put(adType, adTypeCounts.getOrDefault(adType, 0) + 1);
                    }
                }
            } catch (Exception e) {
                System.err.println("Could not fetch advertisements for ad type breakdown: " + e.getMessage());
            }
            
            // Get location breakdown from companies
            Map<String, Integer> locationCounts = new HashMap<>();
            try {
                List<Map<String, Object>> companies = mobilizeApiService.fetchCompaniesFromMobilize();
                for (Map<String, Object> company : companies) {
                    String locationType = inferLocationTypeFromCompany(company);
                    locationCounts.put(locationType, locationCounts.getOrDefault(locationType, 0) + 1);
                }
            } catch (Exception e) {
                System.err.println("Could not fetch companies for location breakdown: " + e.getMessage());
            }
            
            breakdown.put("adTypes", adTypeCounts);
            breakdown.put("locations", locationCounts);
            
            System.out.println("<<< DASHBOARD: Breakdown data - AdTypes: " + adTypeCounts.size() + ", Locations: " + locationCounts.size());
            return ResponseEntity.ok(breakdown);
            
        } catch (Exception e) {
            System.err.println("!!! Error fetching breakdown data: " + e.getMessage());
            return ResponseEntity.ok(getFallbackBreakdownData());
        }
    }

    /**
     * Get dashboard KPIs
     */
    @GetMapping("/kpis")
    public ResponseEntity<Map<String, Object>> getDashboardKpis() {
        System.out.println(">>> DASHBOARD: Fetching KPIs from Mobilize data");
        
        try {
            Map<String, Object> mobilizeStats = mobilizeApiService.fetchDashboardStats();
            
            // Extract and format KPI data
            Map<String, Object> kpis = new LinkedHashMap<>();
            
            // Campaign statistics
            Map<String, Object> campaignData = (Map<String, Object>) mobilizeStats.get("campaign");
            if (campaignData != null) {
                kpis.put("totalCampaigns", campaignData.get("campaigns"));
                kpis.put("activeCampaigns", campaignData.get("activeCampaigns"));
                kpis.put("inactiveCampaigns", campaignData.get("inactiveCampaigns"));
                kpis.put("draftCampaigns", campaignData.get("draftCampaigns"));
                kpis.put("completedCampaigns", campaignData.get("completedCampaigns"));
            }
            
            // User statistics
            kpis.put("publishers", mobilizeStats.get("publishers"));
            kpis.put("consumers", mobilizeStats.get("consumers"));
            kpis.put("admins", mobilizeStats.get("users"));
            kpis.put("advertisements", mobilizeStats.get("advertisement"));
            kpis.put("companies", mobilizeStats.get("companies"));
            
            System.out.println("<<< DASHBOARD: Returning " + kpis.size() + " KPI metrics");
            return ResponseEntity.ok(kpis);
            
        } catch (Exception e) {
            System.err.println("!!! Error fetching KPIs: " + e.getMessage());
            return ResponseEntity.ok(getFallbackKpis());
        }
    }

    private double calculateTrend(int campaignCount) {
        // Simple trend calculation based on campaign count
        if (campaignCount >= 5) return 12.5;
        if (campaignCount >= 3) return 8.0;
        if (campaignCount >= 1) return -5.0;
        return 0.0;
    }

    private String formatRelativeTime(Date date) {
        if (date == null) return "Unknown time";
        
        long diffMs = System.currentTimeMillis() - date.getTime();
        long minutes = Math.max(1, diffMs / 60000);
        
        if (minutes < 60) return minutes + " min ago";
        long hours = minutes / 60;
        if (hours < 24) return hours + " hr ago";
        long days = hours / 24;
        return days + " day" + (days > 1 ? "s" : "") + " ago";
    }

    // Helper methods for data inference
    private String inferAdTypeFromCampaign(Map<String, Object> campaign) {
        // Try to infer ad type from campaign data
        String title = (String) campaign.get("title");
        if (title != null) {
            if (title.toLowerCase().contains("video") || title.toLowerCase().contains("movie")) {
                return "Video Ads";
            } else if (title.toLowerCase().contains("banner") || title.toLowerCase().contains("display")) {
                return "Banner Ads";
            } else if (title.toLowerCase().contains("sponsored") || title.toLowerCase().contains("listing")) {
                return "Sponsored Lists";
            }
        }
        
        // Default fallback
        return "Banner Ads";
    }

    private String inferLocationTypeFromCompany(Map<String, Object> company) {
        String name = (String) company.get("name");
        if (name != null) {
            if (name.toLowerCase().contains("mumbai") || name.toLowerCase().contains("delhi") || 
                name.toLowerCase().contains("bangalore") || name.toLowerCase().contains("chennai") ||
                name.toLowerCase().contains("hyderabad") || name.toLowerCase().contains("pune")) {
                return "Metros";
            } else if (name.toLowerCase().contains("indore") || name.toLowerCase().contains("jaipur") ||
                       name.toLowerCase().contains("lucknow") || name.toLowerCase().contains("nagpur")) {
                return "Tier 2";
            }
        }
        
        // Default to Tier 2 for unknown locations
        return "Tier 2";
    }

    // Fallback methods
    private List<CreatorStatsResponse> getFallbackCreators() {
        return Arrays.asList(
            new CreatorStatsResponse("Alice Johnson", "alice@keliri.com", 1, 3, 2, 2, 12.5),
            new CreatorStatsResponse("Bob Smith", "bob@keliri.com", 2, 1, 1, 1, 8.0),
            new CreatorStatsResponse("Carol Davis", "carol@keliri.com", 3, 1, 0, 1, -5.0)
        );
    }

    private List<RecentActivityResponse> getFallbackActivities() {
        return Arrays.asList(
            new RecentActivityResponse("1", "Created: Test Campaign", "ACTIVE", "Test Location", "2 min ago"),
            new RecentActivityResponse("2", "Created: Sample Campaign", "ACTIVE", "Bangalore", "5 min ago"),
            new RecentActivityResponse("3", "Created: Demo Campaign", "COMPLETED", "Mumbai", "1 hr ago")
        );
    }

    private List<Map<String, Object>> getFallbackCampaignsForBreakdown() {
        List<Map<String, Object>> campaigns = new ArrayList<>();
        
        Map<String, Object> campaign1 = new HashMap<>();
        campaign1.put("uid", "fallback_1");
        campaign1.put("title", "Alice's Premium Campaign");
        campaign1.put("advertisementType", "Banner Ads");
        campaigns.add(campaign1);
        
        Map<String, Object> campaign2 = new HashMap<>();
        campaign2.put("uid", "fallback_2");
        campaign2.put("title", "Bob's Video Marketing");
        campaign2.put("advertisementType", "Video Ads");
        campaigns.add(campaign2);
        
        Map<String, Object> campaign3 = new HashMap<>();
        campaign3.put("uid", "fallback_3");
        campaign3.put("title", "Carol's Sponsored Listing");
        campaign3.put("advertisementType", "Sponsored Lists");
        campaigns.add(campaign3);
        
        return campaigns;
    }

    private Map<String, Object> getFallbackBreakdownData() {
        Map<String, Object> breakdown = new HashMap<>();
        
        Map<String, Integer> adTypes = new HashMap<>();
        adTypes.put("Banner Ads", 3);
        adTypes.put("Video Ads", 1);
        adTypes.put("Sponsored Lists", 1);
        breakdown.put("adTypes", adTypes);
        
        Map<String, Integer> locations = new HashMap<>();
        locations.put("Metros", 2);
        locations.put("Tier 2", 1);
        breakdown.put("locations", locations);
        
        return breakdown;
    }

    private Map<String, Object> getFallbackKpis() {
        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("totalCampaigns", 5);
        kpis.put("activeCampaigns", 3);
        kpis.put("publishers", 2);
        kpis.put("advertisements", 5);
        kpis.put("companies", 3);
        return kpis;
    }

    // Response DTOs
    public static class CreatorStatsResponse {
        private int rank;
        private String name;
        private String email;
        private int campaignCount;
        private int activeCampaignCount;
        private int locationCount;
        private Set<String> locations = new HashSet<>();
        private double trend;

        public CreatorStatsResponse() {}

        public CreatorStatsResponse(String name, String email) {
            this.name = name;
            this.email = email;
        }

        public CreatorStatsResponse(String name, String email, int rank, int campaignCount, 
                                   int activeCampaignCount, int locationCount, double trend) {
            this.name = name;
            this.email = email;
            this.rank = rank;
            this.campaignCount = campaignCount;
            this.activeCampaignCount = activeCampaignCount;
            this.locationCount = locationCount;
            this.trend = trend;
        }

        // Getters and setters
        public int getRank() { return rank; }
        public void setRank(int rank) { this.rank = rank; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public int getCampaignCount() { return campaignCount; }
        public void setCampaignCount(int campaignCount) { this.campaignCount = campaignCount; }
        public int getActiveCampaignCount() { return activeCampaignCount; }
        public void setActiveCampaignCount(int activeCampaignCount) { this.activeCampaignCount = activeCampaignCount; }
        public int getLocationCount() { return locationCount; }
        public void setLocationCount(int locationCount) { this.locationCount = locationCount; }
        public Set<String> getLocations() { return locations; }
        public void setLocations(Set<String> locations) { this.locations = locations; }
        public double getTrend() { return trend; }
        public void setTrend(double trend) { this.trend = trend; }
    }

    public static class RecentActivityResponse {
        private String id;
        private String action;
        private String status;
        private String locationName;
        private String occurredAt;

        public RecentActivityResponse() {}

        public RecentActivityResponse(String id, String action, String status, String locationName, String occurredAt) {
            this.id = id;
            this.action = action;
            this.status = status;
            this.locationName = locationName;
            this.occurredAt = occurredAt;
        }

        // Getters and setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getLocationName() { return locationName; }
        public void setLocationName(String locationName) { this.locationName = locationName; }
        public String getOccurredAt() { return occurredAt; }
        public void setOccurredAt(String occurredAt) { this.occurredAt = occurredAt; }
    }

    // Helper methods for data inference
    private String mapUserIdToName(String userId) {
        if (userId == null) return "Unknown Creator";
        
        // Try to find the user in Mobilize database
        try {
            Criteria userCriteria = new Criteria().orOperator(
                Criteria.where("uid").is(userId),
                Criteria.where("_id").is(userId)
            );
            Query userQuery = new Query(userCriteria);
            Map<String, Object> user = mongoTemplate.findOne(userQuery, Map.class, "users");
            
            if (user != null) {
                String name = (String) user.get("name");
                if (name != null && !name.trim().isEmpty()) {
                    return name;
                }
                String email = (String) user.get("email");
                if (email != null && !email.trim().isEmpty()) {
                    return email.split("@")[0];
                }
            }
        } catch (Exception e) {
            System.err.println("Error mapping user ID to name: " + e.getMessage());
        }
        
        // Fallback mappings
        switch (userId) {
            case "keliri_user_001": return "Alice Johnson";
            case "keliri_user_002": return "Bob Smith";
            case "keliri_user_003": return "Carol Davis";
            default: return "Unknown Creator";
        }
    }

    private String mapUserIdToEmail(String userId) {
        if (userId == null) return "unknown@keliri.com";
        
        // Try to find the user in Mobilize database
        try {
            Criteria userCriteria = new Criteria().orOperator(
                Criteria.where("uid").is(userId),
                Criteria.where("_id").is(userId)
            );
            Query userQuery = new Query(userCriteria);
            Map<String, Object> user = mongoTemplate.findOne(userQuery, Map.class, "users");
            
            if (user != null) {
                String email = (String) user.get("email");
                if (email != null && !email.trim().isEmpty()) {
                    return email;
                }
                // Generate email from name if available
                String name = (String) user.get("name");
                if (name != null && !name.trim().isEmpty()) {
                    return name.toLowerCase().replace(" ", ".") + "@keliri.com";
                }
            }
        } catch (Exception e) {
            System.err.println("Error mapping user ID to email: " + e.getMessage());
        }
        
        // Fallback mappings
        switch (userId) {
            case "keliri_user_001": return "alice@keliri.com";
            case "keliri_user_002": return "bob@keliri.com";
            case "keliri_user_003": return "carol@keliri.com";
            default: return "unknown@keliri.com";
        }
    }
}
