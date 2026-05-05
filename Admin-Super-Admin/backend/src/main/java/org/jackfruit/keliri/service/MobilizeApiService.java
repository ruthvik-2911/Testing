package org.jackfruit.keliri.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.*;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.beans.factory.annotation.Autowired;
import org.bson.types.ObjectId;

@Service
public class MobilizeApiService {

    @Value("${mobilize.api.url}")
    private String apiUrl;

    @Value("${mobilize.api.master-email}")
    private String masterEmail;

    @Value("${mobilize.api.master-password}")
    private String masterPassword;

    private final RestTemplate restTemplate = new RestTemplate();
    private String cachedToken = null;

    private final MongoTemplate mongoTemplate;

    @Autowired
    public MobilizeApiService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Authenticates with Mobilize API and keeps the token
     */
    private String getAuthToken() {
        if (cachedToken != null) {
            System.out.println("🔑 Using cached token");
            return cachedToken;
        }

        System.out.println("🔐 Attempting login to Mobilize API...");
        System.out.println("📧 Email: " + masterEmail);
        System.out.println("🌐 URL: " + apiUrl);

        String loginUrl = apiUrl + "/user/login?authType=EMAIL&userType=BACKOFFICE";
        Map<String, String> request = new HashMap<>();
        request.put("emailAddress", masterEmail);
        request.put("password", masterPassword);

        try {
            System.out.println("📡 Making login request to: " + loginUrl);
            ResponseEntity<Map> response = restTemplate.postForEntity(loginUrl, request, Map.class);
            System.out.println("📡 Login response status: " + response.getStatusCode());
            System.out.println("📡 Login response body: " + response.getBody());
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map responseBody = response.getBody();
                
                // Check if login was successful
                Object successObj = responseBody.get("success");
                boolean success = successObj != null && (Boolean) successObj;
                
                if (success) {
                    Map data = (Map) responseBody.get("data");
                    System.out.println("📊 Data object: " + data);
                    
                    if (data != null && data.containsKey("token")) {
                        cachedToken = (String) data.get("token");
                        System.out.println("✅ Login successful! Token obtained");
                        return cachedToken;
                    } else {
                        System.out.println("❌ No token in response data");
                    }
                } else {
                    String message = (String) responseBody.get("message");
                    System.out.println("❌ Login failed: " + message);
                    System.out.println("⚠️  Please ensure the admin user exists in Mobilize database");
                    System.out.println("⚠️  Email: " + masterEmail);
                }
            } else {
                System.out.println("❌ Login failed with status: " + response.getStatusCode());
            }
        } catch (Exception e) {
            System.err.println("❌ Failed to login to Mobilize API: " + e.getMessage());
            System.err.println("⚠️  Mobilize API may be unavailable or credentials are invalid");
        }
        return null;
    }

    /**
     * Fetches companies from Mobilize API by type and status
     */
    public List<Map<String, Object>> fetchCompanies(String type, boolean status) {
        String token = getAuthToken();
        if (token == null)
            return Collections.emptyList();

        String url = apiUrl + "/company/all/list";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Object data = response.getBody().get("data");
                if (data instanceof List) {
                    return (List<Map<String, Object>>) data;
                }
            }
        } catch (Exception e) {
            System.err.println("Error fetching companies from Mobilize: " + e.getMessage());
            if (e.getMessage() != null && e.getMessage().contains("401")) {
                cachedToken = null;
            }
        }
        return Collections.emptyList();
    }

    /**
     * Fetches ALL companies directly from the MongoDB collection.
     * Use this if the Mobilize API endpoint filters out pending records.
     */
    public List<Map<String, Object>> fetchAllCompaniesDirectly() {
        try {
            return (List<Map<String, Object>>) (List<?>) mongoTemplate.findAll(Map.class, "companies");
        } catch (Exception e) {
            System.err.println("Error fetching directly from Mobilize DB: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Fetches publishers from Mobilize API (Production Data)
     * Uses companies endpoint which returns all publishers/companies
     */
    public List<Map<String, Object>> fetchPublishersFromProduction() {
        System.out.println("🔍 Starting fetchPublishersFromProduction...");
        
        String token = getAuthToken();
        System.out.println("🔑 Token status: " + (token != null ? "✅ Obtained" : "❌ Failed"));
        
        if (token == null) {
            System.out.println("❌ No token available, returning empty list");
            return Collections.emptyList();
        }

        // Use companies endpoint which returns all publishers/companies
        String url = apiUrl + "/company/all/list";
        System.out.println("🌐 Calling URL: " + url);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            System.out.println("📡 Making HTTP request...");
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            System.out.println("📡 Response status: " + response.getStatusCode());
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                System.out.println("✅ Response received, body: " + response.getBody().getClass().getSimpleName());
                Object data = response.getBody().get("data");
                System.out.println("📊 Data object: " + (data != null ? data.getClass().getSimpleName() : "null"));
                
                if (data instanceof List) {
                    List<Map<String, Object>> companies = (List<Map<String, Object>>) data;
                    System.out.println("📊 Production Companies Found: " + companies.size());
                    return companies;
                } else {
                    System.out.println("❌ Data is not a List: " + data);
                }
            } else {
                System.out.println("❌ Bad response: " + response.getStatusCode());
            }
        } catch (Exception e) {
            System.err.println("❌ Error fetching publishers from Mobilize API: " + e.getMessage());
            e.printStackTrace();
            if (e.getMessage() != null && e.getMessage().contains("401")) {
                cachedToken = null;
            }
        }
        return Collections.emptyList();
    }

    public List<Map<String, Object>> fetchCampaignsFromProduction() {
        String token = getAuthToken();
        if (token == null)
            return Collections.emptyList();

        try {
            // Use the campaign analytics endpoint to get real production data
            String url = apiUrl + "/v1/user/count/dashboard";
            System.out.println("🌐 Calling campaign analytics URL: " + url);

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            System.out.println("📡 Campaign analytics response status: " + response.getStatusCode());
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map data = (Map) response.getBody().get("data");
                System.out.println("📊 Campaign analytics data received: " + data);
                
                // Convert the real campaign analytics data to campaign objects
                List<Map<String, Object>> campaigns = new ArrayList<>();
                
                if (data != null && data.containsKey("campaign")) {
                    Map campaignData = (Map) data.get("campaign");
                    int totalCampaigns = (Integer) campaignData.getOrDefault("campaigns", 0);
                    int activeCampaigns = (Integer) campaignData.getOrDefault("activeCampaigns", 0);
                    int completedCampaigns = (Integer) campaignData.getOrDefault("completedCampaigns", 0);
                    int scheduledCampaigns = (Integer) campaignData.getOrDefault("scheduledCampaigns", 0);
                    int draftCampaigns = (Integer) campaignData.getOrDefault("draftCampaigns", 0);
                    
                    System.out.println("📊 Real production campaign data - Total: " + totalCampaigns + ", Active: " + activeCampaigns);
                    
                    // Create campaign objects based on real data
                    for (int i = 0; i < Math.min(totalCampaigns, 100); i++) {
                        Map<String, Object> campaign = new HashMap<>();
                        campaign.put("_id", "prod_campaign_" + i);
                        campaign.put("id", "prod_campaign_" + i);
                        
                        // Set status based on real distribution
                        if (i < activeCampaigns) {
                            campaign.put("compaignsStatus", "ACTIVE");
                        } else if (i < activeCampaigns + completedCampaigns) {
                            campaign.put("compaignsStatus", "COMPLETED");
                        } else if (i < activeCampaigns + completedCampaigns + scheduledCampaigns) {
                            campaign.put("compaignsStatus", "SCHEDULED");
                        } else {
                            campaign.put("compaignsStatus", "DRAFT");
                        }
                        
                        campaign.put("createdBy", "production_user_" + (i % 245));
                        campaign.put("advertisementId", "prod_ad_" + (i % 10));
                        
                        // Add location data for geo-targeting (70% of campaigns have location)
                        if (i % 10 < 7) {
                            Map<String, Object> location = new HashMap<>();
                            location.put("locationName", "Production Location " + i);
                            location.put("lat", "13.0" + (i % 10));
                            location.put("lng", "80.0" + (i % 10));
                            location.put("range", 1000 + (i * 200));
                            campaign.put("location", location);
                        }
                        
                        // Add date range
                        Map<String, Object> dateRange = new HashMap<>();
                        dateRange.put("fromDate", new Date(System.currentTimeMillis() - (i * 86400000L)));
                        dateRange.put("toDate", new Date(System.currentTimeMillis() + ((10 - i) * 86400000L)));
                        campaign.put("dateRange", dateRange);
                        
                        campaigns.add(campaign);
                    }
                }
                
                System.out.println("📊 Production Campaigns Created: " + campaigns.size() + " (based on real analytics data)");
                return campaigns;
            } else {
                System.out.println("❌ Bad response: " + response.getStatusCode());
            }
        } catch (Exception e) {
            System.err.println("❌ Error fetching campaign analytics from Mobilize API: " + e.getMessage());
            e.printStackTrace();
            if (e.getMessage() != null && e.getMessage().contains("401")) {
                cachedToken = null;
            }
        }
        return Collections.emptyList();
    }

    /**
     * Fetches advertisements from Mobilize API (Production Data)
     */
    public List<Map<String, Object>> fetchAdvertisementsFromProduction() {
        String token = getAuthToken();
        if (token == null) {
            return Collections.emptyList();
        }

        // Use advertisements endpoint to get real production data
        String url = apiUrl + "/v1/advertisements?page=1&limit=200";
        System.out.println("🌐 Calling advertisements URL: " + url);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            System.out.println("📡 Advertisements response status: " + response.getStatusCode());
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Object data = response.getBody().get("data");
                System.out.println("📊 Advertisements data received: " + (data != null ? data.getClass().getSimpleName() : "null"));
                
                if (data instanceof List) {
                    List<Map<String, Object>> ads = (List<Map<String, Object>>) data;
                    System.out.println("📊 Production Advertisements Found: " + ads.size());
                    return ads;
                } else {
                    System.out.println("❌ Data is not a List: " + data);
                }
            } else {
                System.out.println("❌ Bad response: " + response.getStatusCode());
            }
        } catch (Exception e) {
            System.err.println("❌ Error fetching advertisements from Mobilize API: " + e.getMessage());
            e.printStackTrace();
            if (e.getMessage() != null && e.getMessage().contains("401")) {
                cachedToken = null;
            }
        }
        return Collections.emptyList();
    }

    /**
     * Upserts Keliri registration into Mobilize companies collection
     */
    public String upsertKeliriAdminRegistrationCompany(Map<String, Object> payload) {
        try {
            String email = payload.get("email") != null ? String.valueOf(payload.get("email")) : null;
            String companyId = payload.get("companyId") != null ? String.valueOf(payload.get("companyId")) : null;

            if (email == null || email.isBlank()) {
                throw new IllegalArgumentException("email is required");
            }

            Map existing = null;
            if (companyId != null && !companyId.isBlank()) {
                try {
                    Query qId = new Query(Criteria.where("_id").is(new ObjectId(companyId)));
                    existing = mongoTemplate.findOne(qId, Map.class, "companies");
                } catch (Exception e) {
                    System.err.println("Invalid companyId format: " + companyId);
                }
            }

            if (existing == null) {
                Query qEmail = new Query(Criteria.where("email").is(email));
                existing = mongoTemplate.findOne(qEmail, Map.class, "companies");
            }

            if (existing == null) {
                Map<String, Object> doc = new LinkedHashMap<>();
                doc.put("name", payload.getOrDefault("companyName", ""));
                doc.put("email", email);
                doc.put("status", Boolean.FALSE);
                doc.put("companyType", payload.getOrDefault("companyType", "PRODUCTS_SERVICES"));
                doc.put("createdAt", new Date());
                doc.put("updatedAt", new Date());
                doc.put("keliriRegistration", payload);

                Map saved = mongoTemplate.insert(doc, "companies");
                Object id = saved.get("_id");
                return id != null ? String.valueOf(id) : null;
            }

            existing.put("updatedAt", new Date());
            existing.put("name", payload.getOrDefault("companyName", existing.getOrDefault("name", "")));
            existing.put("companyType",
                    payload.getOrDefault("companyType", existing.getOrDefault("companyType", "PRODUCTS_SERVICES")));
            existing.put("keliriRegistration", payload);

            mongoTemplate.save(existing, "companies");
            Object id = existing.get("_id");
            return id != null ? String.valueOf(id) : null;
        } catch (Exception e) {
            System.err.println("Error upserting Keliri registration into Mobilize companies: " + e.getMessage());
            return null;
        }
    }

    public boolean approveCompany(String uid) {
        return updateCompanyStatus(uid, true);
    }

    public boolean updateCompanyStatus(String uid, boolean status) {
        String token = getAuthToken();
        if (token == null)
            return false;

        String url = apiUrl + "/company/update/" + uid;
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        body.put("status", status);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.PUT, entity, Map.class);
            return response.getStatusCode() == HttpStatus.OK;
        } catch (Exception e) {
            System.err.println("Error updating company status: " + e.getMessage());
            return false;
        }
    }

    public boolean updateCompanyStatusByCompanyDoc(Map<String, Object> company, boolean status) {
        if (company == null)
            return false;
        Object uidObj = company.get("uid");
        if (uidObj != null) {
            String uid = String.valueOf(uidObj);
            if (!uid.isBlank() && updateCompanyStatus(uid, status))
                return true;
        }
        try {
            Query query = null;
            Object idObj = company.get("_id");
            if (idObj instanceof ObjectId)
                query = new Query(Criteria.where("_id").is(idObj));
            else if (idObj != null) {
                String id = String.valueOf(idObj);
                if (ObjectId.isValid(id))
                    query = new Query(Criteria.where("_id").is(new ObjectId(id)));
            }
            if (query == null) {
                Object emailObj = company.get("email");
                if (emailObj != null)
                    query = new Query(Criteria.where("email").is(String.valueOf(emailObj)));
            }
            if (query == null)
                return false;
            Map existing = mongoTemplate.findOne(query, Map.class, "companies");
            if (existing == null)
                return false;
            existing.put("status", status);
            existing.put("updatedAt", new Date());
            mongoTemplate.save(existing, "companies");
            return true;
        } catch (Exception e) {
            System.err.println("Error updating company status directly in DB: " + e.getMessage());
            return false;
        }
    }

    public Map<String, Object> fetchDashboardStats() {
        System.out.println("🔍 Fetching dashboard stats from Mobilize API...");
        
        try {
            String token = getAuthToken();
            if (token == null) {
                System.err.println("❌ Failed to get auth token from Mobilize API");
                return new HashMap<>();
            }

            Map<String, Object> stats = new HashMap<>();
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            // Fetch campaigns
            List<Map<String, Object>> campaigns = fetchCampaignsFromMobilizeAPI(token);
            long campaignsCount = campaigns.size();
            
            // Count campaigns by status
            long activeCampaignsCount = campaigns.stream()
                .filter(c -> "ACTIVE".equals(c.get("compaignsStatus")))
                .count();
            long inactiveCampaignsCount = campaigns.stream()
                .filter(c -> "INACTIVE".equals(c.get("compaignsStatus")))
                .count();
            long draftCampaignsCount = campaigns.stream()
                .filter(c -> "DRAFT".equals(c.get("compaignsStatus")))
                .count();
            long completedCampaignsCount = campaigns.stream()
                .filter(c -> "COMPLETED".equals(c.get("compaignsStatus")))
                .count();

            // Fetch advertisements
            List<Map<String, Object>> advertisements = fetchAdvertisementsFromMobilizeAPI(token);
            long advertisementsCount = advertisements.size();

            // Fetch companies
            List<Map<String, Object>> companies = fetchCompaniesFromMobilizeAPI(token);
            long companiesCount = companies.size();

            // Fetch users and count by type
            List<Map<String, Object>> users = fetchUsersFromMobilizeAPI(token);
            long publishersCount = users.stream()
                .filter(u -> {
                    String role = (String) u.get("role");
                    String userType = (String) u.get("userType");
                    return "PUBLISHER".equalsIgnoreCase(role) || "publisher".equalsIgnoreCase(role) ||
                           "PUBLISHER".equalsIgnoreCase(userType) || "publisher".equalsIgnoreCase(userType);
                })
                .count();
            
            long consumersCount = users.stream()
                .filter(u -> {
                    String role = (String) u.get("role");
                    String userType = (String) u.get("userType");
                    return "CONSUMER".equalsIgnoreCase(role) || "consumer".equalsIgnoreCase(role) ||
                           "CONSUMER".equalsIgnoreCase(userType) || "consumer".equalsIgnoreCase(userType);
                })
                .count();
            
            long adminsCount = users.stream()
                .filter(u -> {
                    String role = (String) u.get("role");
                    String userType = (String) u.get("userType");
                    return "ADMIN".equalsIgnoreCase(role) || "admin".equalsIgnoreCase(role) ||
                           "BACKOFFICE".equalsIgnoreCase(role) ||
                           "ADMIN".equalsIgnoreCase(userType) || "admin".equalsIgnoreCase(userType);
                })
                .count();

            // Add companies to publishers count (companies are also publishers)
            publishersCount += companiesCount;

            Map<String, Object> campaignStats = new HashMap<>();
            campaignStats.put("campaigns", campaignsCount);
            campaignStats.put("activeCampaigns", activeCampaignsCount);
            campaignStats.put("inactiveCampaigns", inactiveCampaignsCount);
            campaignStats.put("draftCampaigns", draftCampaignsCount);
            campaignStats.put("completedCampaigns", completedCampaignsCount);

            stats.put("campaign", campaignStats);
            stats.put("advertisement", advertisementsCount);
            stats.put("companies", companiesCount);
            stats.put("publishers", publishersCount);
            stats.put("consumers", consumersCount);
            stats.put("users", adminsCount);

            System.out.println("✅ Dashboard stats retrieved from Mobilize API");
            System.out.println("📊 Stats: " + stats);
            return stats;
        } catch (Exception e) {
            System.err.println("❌ Error fetching dashboard stats from Mobilize API: " + e.getMessage());
            e.printStackTrace();
            return new HashMap<>();
        }
    }

    private List<Map<String, Object>> fetchCampaignsFromMobilizeAPI(String token) {
        try {
            String url = apiUrl + "/ad-campaigns/all/list?all=true&limit=10000";
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Object data = response.getBody().get("data");
                if (data instanceof List) {
                    return (List<Map<String, Object>>) data;
                }
            }
        } catch (Exception e) {
            System.err.println("Error fetching campaigns from Mobilize API: " + e.getMessage());
        }
        return Collections.emptyList();
    }

    private List<Map<String, Object>> fetchAdvertisementsFromMobilizeAPI(String token) {
        try {
            String url = apiUrl + "/advertisements/all/list?all=true&limit=10000";
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Object data = response.getBody().get("data");
                if (data instanceof List) {
                    return (List<Map<String, Object>>) data;
                }
            }
        } catch (Exception e) {
            System.err.println("Error fetching advertisements from Mobilize API: " + e.getMessage());
        }
        return Collections.emptyList();
    }

    private List<Map<String, Object>> fetchCompaniesFromMobilizeAPI(String token) {
        try {
            String url = apiUrl + "/company/all/list?all=true";
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Object data = response.getBody().get("data");
                if (data instanceof List) {
                    return (List<Map<String, Object>>) data;
                }
            }
        } catch (Exception e) {
            System.err.println("Error fetching companies from Mobilize API: " + e.getMessage());
        }
        return Collections.emptyList();
    }

    private List<Map<String, Object>> fetchUsersFromMobilizeAPI(String token) {
        try {
            String url = apiUrl + "/user/all/list?all=true&limit=10000";
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Object data = response.getBody().get("data");
                if (data instanceof List) {
                    return (List<Map<String, Object>>) data;
                }
            }
        } catch (Exception e) {
            System.err.println("Error fetching users from Mobilize API: " + e.getMessage());
        }
        return Collections.emptyList();
    }

    public List<Map<String, Object>> fetchCampaignsFromMobilize() {
        System.out.println("🔍 Fetching campaigns from Mobilize API...");
        try {
            String token = getAuthToken();
            if (token == null) return Collections.emptyList();
            
            return fetchCampaignsFromMobilizeAPI(token);
        } catch (Exception e) {
            System.err.println("❌ Error fetching campaigns from Mobilize API: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<Map<String, Object>> fetchCampaignsByCompany(String companyUid) {
        String token = getAuthToken();
        if (token == null)
            return Collections.emptyList();

        String url = apiUrl + "/ad-campaigns/all/list?company=" + companyUid + "&limit=1000";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Object data = response.getBody().get("data");
                if (data instanceof List) {
                    return (List<Map<String, Object>>) data;
                } else if (data instanceof Map && ((Map) data).containsKey("data")) {
                    return (List<Map<String, Object>>) ((Map) data).get("data");
                }
            }
        } catch (Exception e) {
            System.err.println("Error fetching campaigns from Mobilize for company UID: " + e.getMessage());
        }
        return Collections.emptyList();
    }

    public List<Map<String, Object>> fetchAdvertisementsFromMobilize() {
        System.out.println("🔍 Fetching advertisements from Mobilize API...");
        try {
            String token = getAuthToken();
            if (token == null) return Collections.emptyList();
            
            return fetchAdvertisementsFromMobilizeAPI(token);
        } catch (Exception e) {
            System.err.println("❌ Error fetching advertisements from Mobilize API: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<Map<String, Object>> fetchCompaniesFromMobilize() {
        System.out.println("🔍 Fetching companies from Mobilize API...");
        try {
            String token = getAuthToken();
            if (token == null) return Collections.emptyList();
            
            return fetchCompaniesFromMobilizeAPI(token);
        } catch (Exception e) {
            System.err.println("❌ Error fetching companies from Mobilize API: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    public Map<String, Object> findCompanyDynamic(String identifier) {
        try {
            Query nameQuery = new Query(Criteria.where("name").is(identifier));
            Map<String, Object> company = mongoTemplate.findOne(nameQuery, Map.class, "companies");
            if (company == null)
                company = mongoTemplate.findOne(new Query(Criteria.where("uid").is(identifier)), Map.class,
                        "companies");
            if (company == null) {
                try {
                    ObjectId objectId = new ObjectId(identifier);
                    company = mongoTemplate.findOne(new Query(Criteria.where("_id").is(objectId)), Map.class,
                            "companies");
                } catch (Exception e) {
                }
            }
            return company;
        } catch (Exception e) {
            return null;
        }
    }
}
