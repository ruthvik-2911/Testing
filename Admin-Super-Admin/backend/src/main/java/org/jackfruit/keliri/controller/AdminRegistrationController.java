package org.jackfruit.keliri.controller;

import org.jackfruit.keliri.model.AdminRegistration;
import org.jackfruit.keliri.repository.AdminRegistrationRepository;
import org.jackfruit.keliri.repository.usersRepository;
import org.jackfruit.keliri.service.FileStorageService;
import org.jackfruit.keliri.service.MobilizeApiService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@CrossOrigin(originPatterns = "*", maxAge = 3600, allowCredentials = "true")
@RestController
@RequestMapping("/api/admin")
public class AdminRegistrationController {

    private final AdminRegistrationRepository registrationRepository;
    private final FileStorageService fileStorageService;
    private final usersRepository usersRepository;
    private final MobilizeApiService mobilizeApiService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AdminRegistrationController(AdminRegistrationRepository registrationRepository,
            FileStorageService fileStorageService,
            usersRepository usersRepository,
            MobilizeApiService mobilizeApiService) {
        this.registrationRepository = registrationRepository;
        this.fileStorageService = fileStorageService;
        this.usersRepository = usersRepository;
        this.mobilizeApiService = mobilizeApiService;
    }

    @PostMapping(value = "/register", consumes = "multipart/form-data")
    public ResponseEntity<?> register(
            @RequestParam("companyName") String companyName,
            @RequestParam(value = "companyType", required = false) String companyType,
            @RequestParam("authorizedPerson") String authorizedPerson,
            @RequestParam("businessAddress") String businessAddress,
            @RequestParam(value = "addressLine2", required = false) String addressLine2,
            @RequestParam(value = "city", required = false) String city,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "zipCode", required = false) String zipCode,
            @RequestParam(value = "country", required = false) String country,
            @RequestParam(value = "gstNumber", required = false) String gstNumber,
            @RequestParam("mobileNumber") String mobileNumber,
            @RequestParam(value = "countryCode", required = false) String countryCode,
            @RequestParam("emailId") String emailId,
            @RequestParam("password") String password,
            @RequestParam(value = "gstCertificate", required = false) MultipartFile gstCertificate,
            @RequestParam("companyRegistrationDoc") MultipartFile companyRegistrationDoc,
            @RequestParam("idProof") MultipartFile idProof) {
        try {
            // Mobilize-only: upload docs to S3, then store full payload in Mobilize "companies"
            String idSuffix = emailId.replaceAll("[^a-zA-Z0-9]", "_");

            String gstUrl = "";
            if (gstCertificate != null && !gstCertificate.isEmpty()) {
                gstUrl = fileStorageService.uploadFile(gstCertificate, idSuffix + "/gst");
            }
            String companyDocUrl = fileStorageService.uploadFile(companyRegistrationDoc, idSuffix + "/company");
            String idProofUrl = fileStorageService.uploadFile(idProof, idSuffix + "/id");

            Map<String, Object> mobilizePayload = new java.util.LinkedHashMap<>();
            mobilizePayload.put("companyName", companyName);
            mobilizePayload.put("companyType", companyType != null ? companyType : "PRODUCTS_SERVICES");
            mobilizePayload.put("authorizedPerson", authorizedPerson);
            mobilizePayload.put("businessAddress", businessAddress);
            mobilizePayload.put("addressLine2", addressLine2 != null ? addressLine2 : "");
            mobilizePayload.put("city", city != null ? city : "");
            mobilizePayload.put("state", state != null ? state : "");
            mobilizePayload.put("zipCode", zipCode != null ? zipCode : "");
            mobilizePayload.put("country", country != null ? country : "India");
            mobilizePayload.put("gstNumber", gstNumber != null ? gstNumber : "");
            mobilizePayload.put("countryCode", countryCode != null ? countryCode : "");
            mobilizePayload.put("mobileNumber", mobileNumber);
            mobilizePayload.put("email", emailId);
            mobilizePayload.put("emailId", emailId);
            // Store hashed password only (do NOT store plaintext)
            mobilizePayload.put("passwordHash", passwordEncoder.encode(password));
            mobilizePayload.put("gstCertificateUrl", gstUrl);
            mobilizePayload.put("companyRegistrationDocUrl", companyDocUrl);
            mobilizePayload.put("idProofUrl", idProofUrl);
            mobilizePayload.put("submittedAt", Instant.now().toString());
            mobilizePayload.put("source", "KELIRI_ADMIN_PORTAL");

            String companyId = mobilizeApiService.upsertKeliriAdminRegistrationCompany(mobilizePayload);
            if (companyId == null) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("success", false, "message", "Failed to save registration in Mobilize database"));
            }

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Registration submitted successfully. Awaiting approval.",
                    "companyId", companyId));
        } catch (software.amazon.awssdk.services.s3.model.S3Exception e) {
            System.err.println("[AdminRegistrationController] S3 Error: " + e.awsErrorDetails().errorMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "AWS S3 Error: " + e.awsErrorDetails().errorMessage()));
        } catch (Exception e) {
            System.err.println("[AdminRegistrationController] General Error: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Error submitting registration: " + e.getMessage()));
        }
    }

    @GetMapping("/status")
    public ResponseEntity<?> getStatus(@RequestParam("email") String email) {
        // 1) Preferred: local registration record (created via /api/admin/register)
        var regOpt = registrationRepository.findByEmailId(email);
        if (regOpt.isPresent()) {
            AdminRegistration reg = regOpt.get();
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "status", reg.getStatus(),
                    "companyName", reg.getCompanyName(),
                    "submittedAt", reg.getSubmittedAt(),
                    "rejectionReason", reg.getRejectionReason() != null ? reg.getRejectionReason() : ""));
        }

        // 2) If already approved and promoted to an active local admin user, return success too.
        var userOpt = usersRepository.findByEmailAddress(email);
        if (userOpt.isPresent()) {
            var user = userOpt.get();
            // For this screen, "approved" means an admin account exists.
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "status", "APPROVED",
                    "companyName", user.getCompanyName() != null ? user.getCompanyName() : "",
                    "submittedAt", Instant.now(),
                    "rejectionReason", ""));
        }

        // 3) Fallback: check Mobilize companies (unified DB). Mobilize uses boolean `status`.
        List<Map> companies = mobilizeApiService.fetchAllCompaniesDirectly();
        Map<?, ?> company = companies.stream()
                .filter(c -> c != null && email.equalsIgnoreCase(String.valueOf(((Map) c).get("email"))))
                .map(c -> (Map<?, ?>) c)
                .findFirst()
                .orElse(null);

        if (company != null) {
            Object statusObj = company.get("status");
            boolean isActive = statusObj instanceof Boolean
                    ? (Boolean) statusObj
                    : Boolean.parseBoolean(String.valueOf(statusObj));

            String companyName = String.valueOf(company.get("name"));
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "status", isActive ? "APPROVED" : "PENDING",
                    "companyName", companyName != null ? companyName : "",
                    "submittedAt", Instant.now(),
                    "rejectionReason", ""));
        }

        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No registration found for this email");
    }

    /**
     * Returns the S3 document URLs for a given registration (by ID).
     * Used by Super Admin to preview/download submitted documents.
     */
    @GetMapping("/{id}/documents")
    public ResponseEntity<?> getDocuments(@PathVariable String id) {
        return registrationRepository.findById(id)
                .map(reg -> {
                    java.util.Map<String, Object> docs = new java.util.LinkedHashMap<>();
                    docs.put("registrationId", reg.getId());
                    docs.put("companyName", reg.getCompanyName());
                    docs.put("emailId", reg.getEmailId());
                    docs.put("gstCertificateUrl", reg.getGstCertificateUrl() != null ? reg.getGstCertificateUrl() : "");
                    docs.put("companyRegistrationDocUrl",
                            reg.getCompanyRegistrationDocUrl() != null ? reg.getCompanyRegistrationDocUrl() : "");
                    docs.put("idProofUrl", reg.getIdProofUrl() != null ? reg.getIdProofUrl() : "");
                    return ResponseEntity.ok(docs);
                })
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Registration not found"));
    }
}
