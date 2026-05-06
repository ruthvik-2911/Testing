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
        if (cachedToken != null)
            return cachedToken;

        String loginUrl = apiUrl + "/user/login?authType=EMAIL&userType=BACKOFFICE";
        Map<String, String> request = new HashMap<>();
        request.put("emailAddress", masterEmail);
        request.put("password", masterPassword);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(loginUrl, request, Map.class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map data = (Map) response.getBody().get("data");
                if (data != null && data.containsKey("token")) {
                    cachedToken = (String) data.get("token");
                    return cachedToken;
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to login to Mobilize API: " + e.getMessage());
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

        // Using /all/list to fetch all companies since status filtering is not
        // supported in production API
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
    public List<Map> fetchAllCompaniesDirectly() {
        try {
            // "companies" is the collection name in Mobilize DB
            return mongoTemplate.findAll(Map.class, "companies");
        } catch (Exception e) {
            System.err.println("Error fetching directly from Mobilize DB: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Creates/updates a Mobilize "companies" record with Keliri admin registration
     * details.
     *
     * We store the full form payload under `keliriRegistration` to avoid guessing /
     * breaking
     * Mobilize's native schema.
     *
     * @return the company document id/uid (best-effort)
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
                // Priority 1: Look up by Company ID (for Joining Existing Company)
                try {
                    Query qId = new Query(Criteria.where("_id").is(new ObjectId(companyId)));
                    existing = mongoTemplate.findOne(qId, Map.class, "companies");
                } catch (Exception e) {
                    System.err.println("Invalid companyId format: " + companyId);
                }
            }

            if (existing == null) {
                // Priority 2: Look up by Email (for New Registrations or fallback)
                Query qEmail = new Query(Criteria.where("email").is(email));
                existing = mongoTemplate.findOne(qEmail, Map.class, "companies");
            }

            if (existing == null) {
                Map<String, Object> doc = new LinkedHashMap<>();
                // Minimal top-level fields commonly used by Mobilize
                doc.put("name", payload.getOrDefault("companyName", ""));
                doc.put("email", email);
                doc.put("status", Boolean.FALSE); // pending by default
                doc.put("companyType", payload.getOrDefault("companyType", "PRODUCTS_SERVICES"));
                doc.put("createdAt", new Date());
                doc.put("updatedAt", new Date());

                // Store full registration details + document URLs
                doc.put("keliriRegistration", payload);

                Map saved = mongoTemplate.insert(doc, "companies");
                Object id = saved.get("_id");
                return id != null ? String.valueOf(id) : null;
            }

            // Update
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

    /**
     * Approves a company by setting its status to true
     */
    public boolean approveCompany(String uid) {
        return updateCompanyStatus(uid, true);
    }

    /**
     * Updates company status in Mobilize (true=active, false=inactive).
     */
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

    /**
     * Fetches advertisements from Mobilize API with company-specific filtering
     */
    public List<Map<String, Object>> fetchAdvertisements(String companyUID, int page, int limit) {
        String token = getAuthToken();
        if (token == null)
            return Collections.emptyList();

        String url = apiUrl + "/advertisements?page=" + page + "&limit=" + limit;
        if (companyUID != null && !companyUID.isBlank()) {
            url += "&companyUID=" + companyUID;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Object data = response.getBody().get("data");
                if (data instanceof List) {
                    List<Map<String, Object>> ads = (List<Map<String, Object>>) data;
                    
                    // Additional client-side filtering to ensure company-specific results
                    if (companyUID != null && !companyUID.isBlank()) {
                        return ads.stream()
                            .filter(ad -> {
                                Object company = ad.get("company");
                                if (company instanceof Map) {
                                    Map<String, Object> companyMap = (Map<String, Object>) company;
                                    Object companyId = companyMap.get("_id");
                                    return companyUID.equals(String.valueOf(companyId));
                                }
                                return companyUID.equals(String.valueOf(company));
                            })
                            .toList();
                    }
                    return ads;
                }
            }
        } catch (Exception e) {
            System.err.println("Error fetching advertisements from Mobilize: " + e.getMessage());
            if (e.getMessage() != null && e.getMessage().contains("401")) {
                cachedToken = null;
            }
        }
        return Collections.emptyList();
    }

    /**
     * Fetches dashboard counts with company-specific filtering
     */
    public Map<String, Object> fetchDashboardCounts(String companyUID) {
        String token = getAuthToken();
        if (token == null)
            return Collections.emptyMap();

        String url = apiUrl + "/user/count/dashboard";
        if (companyUID != null && !companyUID.isBlank()) {
            url += "?companyUID=" + companyUID;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return (Map<String, Object>) response.getBody().get("data");
            }
        } catch (Exception e) {
            System.err.println("Error fetching dashboard counts from Mobilize: " + e.getMessage());
            if (e.getMessage() != null && e.getMessage().contains("401")) {
                cachedToken = null;
            }
        }
        return Collections.emptyMap();
    }

    /**
     * Updates company status with a resilient strategy:
     * 1) Try Mobilize API update using `uid` if present
     * 2) Fallback to direct Mongo update by `_id` / `email`
     */
    public boolean updateCompanyStatusByCompanyDoc(Map<String, Object> company, boolean status) {
        if (company == null) {
            return false;
        }

        // Prefer API path when uid exists
        Object uidObj = company.get("uid");
        if (uidObj != null) {
            String uid = String.valueOf(uidObj);
            if (!uid.isBlank() && updateCompanyStatus(uid, status)) {
                return true;
            }
        }

        // Fallback: direct DB update in Mobilize "companies"
        try {
            Query query = null;
            Object idObj = company.get("_id");
            if (idObj instanceof ObjectId) {
                query = new Query(Criteria.where("_id").is(idObj));
            } else if (idObj != null) {
                String id = String.valueOf(idObj);
                if (ObjectId.isValid(id)) {
                    query = new Query(Criteria.where("_id").is(new ObjectId(id)));
                }
            }

            if (query == null) {
                Object emailObj = company.get("email");
                if (emailObj != null) {
                    query = new Query(Criteria.where("email").is(String.valueOf(emailObj)));
                }
            }

            if (query == null) {
                return false;
            }

            Map existing = mongoTemplate.findOne(query, Map.class, "companies");
            if (existing == null) {
                return false;
            }

            existing.put("status", status);
            existing.put("updatedAt", new Date());
            mongoTemplate.save(existing, "companies");
            return true;
        } catch (Exception e) {
            System.err.println("Error updating company status directly in DB: " + e.getMessage());
            return false;
        }
    }
}
