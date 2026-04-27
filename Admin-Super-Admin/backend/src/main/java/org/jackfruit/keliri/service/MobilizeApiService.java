package org.jackfruit.keliri.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.*;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.beans.factory.annotation.Autowired;

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
     * Approves a company by setting its status to true
     */
    public boolean approveCompany(String uid) {
        String token = getAuthToken();
        if (token == null)
            return false;

        String url = apiUrl + "/company/update/" + uid;

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        body.put("status", true);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.PUT, entity, Map.class);
            return response.getStatusCode() == HttpStatus.OK;
        } catch (Exception e) {
            System.err.println("Error approving company: " + e.getMessage());
            return false;
        }
    }
}
