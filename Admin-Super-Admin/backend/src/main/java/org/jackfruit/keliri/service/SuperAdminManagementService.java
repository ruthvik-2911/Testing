package org.jackfruit.keliri.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import org.bson.types.ObjectId;
import org.jackfruit.keliri.model.PaymentTransaction;
import org.jackfruit.keliri.model.SuperAdminManagementResponse;
import org.jackfruit.keliri.model.ad_campaigns;
import org.jackfruit.keliri.model.advertisements;
import org.jackfruit.keliri.model.txn_user_locations;
import org.jackfruit.keliri.model.users;
import org.jackfruit.keliri.repository.PaymentTransactionRepository;
import org.jackfruit.keliri.repository.ad_campaignsRepository;
import org.jackfruit.keliri.repository.advertisementsRepository;
import org.jackfruit.keliri.repository.hitRecordRepository;
import org.jackfruit.keliri.repository.txn_user_locationsRepository;
import org.jackfruit.keliri.repository.usersRepository;
import org.jackfruit.keliri.repository.PublisherRepository;
import com.razorpay.Payment;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class SuperAdminManagementService {
    private static final ZoneId ZONE_ID = ZoneId.systemDefault();
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    private final usersRepository usersRepository;
    private final ad_campaignsRepository campaignsRepository;
    private final advertisementsRepository advertisementsRepository;
    private final txn_user_locationsRepository locationsRepository;
    private final hitRecordRepository hitRecordRepository;
    private final org.jackfruit.keliri.repository.AdminRegistrationRepository registrationRepository;
    private final PublisherRepository publisherRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final RazorpayClient razorpayClient;
    private final org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder passwordEncoder = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();

    private final List<SuperAdminManagementResponse.EmailNotificationRecord> emailNotifications = new CopyOnWriteArrayList<>();
    private final List<SuperAdminManagementResponse.AuditLogRecord> actionAuditLogs = new CopyOnWriteArrayList<>();

    private final MobilizeApiService mobilizeApiService;

    public SuperAdminManagementService(
            usersRepository usersRepository,
            ad_campaignsRepository campaignsRepository,
            advertisementsRepository advertisementsRepository,
            txn_user_locationsRepository locationsRepository,
            hitRecordRepository hitRecordRepository,
            org.jackfruit.keliri.repository.AdminRegistrationRepository registrationRepository,
            PublisherRepository publisherRepository,
            PaymentTransactionRepository paymentTransactionRepository,
            RazorpayClient razorpayClient,
            MobilizeApiService mobilizeApiService) {
        this.usersRepository = usersRepository;
        this.campaignsRepository = campaignsRepository;
        this.advertisementsRepository = advertisementsRepository;
        this.locationsRepository = locationsRepository;
        this.hitRecordRepository = hitRecordRepository;
        this.registrationRepository = registrationRepository;
        this.publisherRepository = publisherRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.razorpayClient = razorpayClient;
        this.mobilizeApiService = mobilizeApiService;
    }

    public List<SuperAdminManagementResponse.AdminRecord> getAdmins(String search, String status) {
        List<SuperAdminManagementResponse.AdminRecord> admins = new ArrayList<>();

        // 1. Get real admins (Active/Suspended) from local DB
        List<SuperAdminManagementResponse.AdminRecord> activeAdmins = usersRepository.findbygivendor().stream()
                .map(this::toAdminRecord)
                .toList();
        admins.addAll(activeAdmins);

        // 2. Get pending/rejected registrations from Mobilize DB directly (to avoid API
        // filtering)
        List<Map> allCompanies = mobilizeApiService.fetchAllCompaniesDirectly();
        for (Map company : allCompanies) {
            String email = (String) company.get("email");
            if (activeAdmins.stream().anyMatch(a -> Objects.equals(a.getEmail(), email)))
                continue;

            admins.add(mobilizeCompanyToAdminRecord((Map<String, Object>) company));
        }

        // 3. Get registrations from admin_registrations collection
        List<org.jackfruit.keliri.model.AdminRegistration> regs = registrationRepository.findAll();
        for (org.jackfruit.keliri.model.AdminRegistration reg : regs) {
            if (admins.stream().anyMatch(a -> Objects.equals(a.getEmail(), reg.getEmailId())))
                continue;

            admins.add(toAdminRecordFromReg(reg));
        }

        // 3. Try finding in Mobilize DB (Unified Discovery) as fallback for missing
        // local records
        try {
            List<Map> rawCompanies = mobilizeApiService.fetchAllCompaniesDirectly();
            if (rawCompanies != null) {
                for (Map<?, ?> rawCompany : rawCompanies) {
                    if (rawCompany == null)
                        continue;

                    @SuppressWarnings("unchecked")
                    Map<String, Object> company = (Map<String, Object>) rawCompany;

                    Object emailObj = company.get("email");
                    String email = emailObj != null ? String.valueOf(emailObj) : null;

                    if (email == null || email.trim().isEmpty()) {
                        Object regObj = company.get("keliriRegistration");
                        if (regObj instanceof Map) {
                            Object innerEmail = ((Map<?, ?>) regObj).get("emailId");
                            email = innerEmail != null ? String.valueOf(innerEmail) : null;
                        }
                    }

                    if (email == null || email.trim().isEmpty())
                        continue;

                    final String finalEmail = email;
                    boolean alreadyInList = admins.stream()
                            .anyMatch(a -> a.getEmail() != null && a.getEmail().equalsIgnoreCase(finalEmail));

                    if (alreadyInList)
                        continue;

                    admins.add(mobilizeCompanyToAdminRecord(company));
                }
            }
        } catch (Exception e) {
            System.err.println("Error syncing Mobilize companies in getAdmins: " + e.getMessage());
        }

        return admins.stream()
                .filter(admin -> matchesAdminFilters(admin, search, status))
                .sorted(Comparator.comparing(SuperAdminManagementResponse.AdminRecord::getRegisteredDate).reversed())
                .toList();
    }

    public SuperAdminManagementResponse.AdminDetail getAdminDetail(String adminId) {
        // Try finding in local active users first
        users admin = usersRepository.findbygivendor().stream()
                .filter(user -> Objects.equals(user.getId(), adminId))
                .findFirst()
                .orElse(null);

        if (admin != null) {
            SuperAdminManagementResponse.AdminDetail detail = new SuperAdminManagementResponse.AdminDetail();
            SuperAdminManagementResponse.AdminRecord base = toAdminRecord(admin);
            copyAdmin(base, detail);

            List<SuperAdminManagementResponse.PublisherRecord> linkedPublishers = getPublishers(null, null, null, null)
                    .stream()
                    .filter(publisher -> adminId.equals(publisher.getAdminId()))
                    .toList();

            List<SuperAdminManagementResponse.PublisherMini> minis = linkedPublishers.stream().map(publisher -> {
                SuperAdminManagementResponse.PublisherMini mini = new SuperAdminManagementResponse.PublisherMini();
                mini.setId(publisher.getId());
                mini.setName(publisher.getName());
                mini.setStatus(publisher.getStatus());
                mini.setAdsPosted(publisher.getAdsPosted());
                return mini;
            }).toList();

            detail.setPublishers(minis);
            detail.setDocuments(buildDocuments(adminId));
            detail.setPerformance(buildPerformance(linkedPublishers));
            return detail;
        }

        // Try finding in Admin Registrations
        org.jackfruit.keliri.model.AdminRegistration reg = registrationRepository.findById(adminId).orElse(null);
        if (reg != null) {
            SuperAdminManagementResponse.AdminDetail detail = new SuperAdminManagementResponse.AdminDetail();
            SuperAdminManagementResponse.AdminRecord base = toAdminRecordFromReg(reg);
            copyAdmin(base, detail);

            detail.setPublishers(new ArrayList<>());
            detail.setDocuments(new ArrayList<>()); // Would normally fetch S3 docs here
            detail.setPerformance(new SuperAdminManagementResponse.PerformanceSummary());
            return detail;
        }

        // Try finding in Mobilize DB (Pending companies)
        Map<String, Object> company = (Map<String, Object>) (Map) mobilizeApiService.fetchAllCompaniesDirectly()
                .stream()
                .filter(c -> Objects.equals(String.valueOf(c.get("uid")), adminId)
                        || Objects.equals(String.valueOf(c.get("_id")), adminId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Admin/Registration not found in Unified DB"));

        SuperAdminManagementResponse.AdminDetail detail = new SuperAdminManagementResponse.AdminDetail();
        SuperAdminManagementResponse.AdminRecord base = mobilizeCompanyToAdminRecord(company);
        copyAdmin(base, detail);

        // Documents + full registration payload (stored under keliriRegistration)
        List<SuperAdminManagementResponse.DocumentItem> docs = new ArrayList<>();
        if (company.get("companyLogo") != null) {
            docs.add(newDocument("Company Logo", "Logo", company.get("companyLogo").toString()));
        }

        Object regObj = company.get("keliriRegistration");
        if (regObj instanceof Map) {
            Map regPayload = (Map) regObj;
            Object gstUrl = regPayload.get("gstCertificateUrl");
            Object companyDocUrl = regPayload.get("companyRegistrationDocUrl");
            Object idProofUrl = regPayload.get("idProofUrl");

            if (gstUrl != null && !String.valueOf(gstUrl).isBlank()) {
                docs.add(newDocument("GST Certificate", "GST", String.valueOf(gstUrl)));
            }
            if (companyDocUrl != null && !String.valueOf(companyDocUrl).isBlank()) {
                docs.add(newDocument("Company Registration Document", "Company", String.valueOf(companyDocUrl)));
            }
            if (idProofUrl != null && !String.valueOf(idProofUrl).isBlank()) {
                docs.add(newDocument("ID Proof", "ID", String.valueOf(idProofUrl)));
            }

            SuperAdminManagementResponse.RegistrationInfo info = new SuperAdminManagementResponse.RegistrationInfo();
            info.setAuthorizedPerson(stringOrEmpty(regPayload.get("authorizedPerson")));
            info.setBusinessAddress(stringOrEmpty(regPayload.get("businessAddress")));
            info.setAddressLine2(stringOrEmpty(regPayload.get("addressLine2")));
            info.setCity(stringOrEmpty(regPayload.get("city")));
            info.setState(stringOrEmpty(regPayload.get("state")));
            info.setZipCode(stringOrEmpty(regPayload.get("zipCode")));
            info.setCountry(stringOrEmpty(regPayload.get("country")));
            info.setGstNumber(stringOrEmpty(regPayload.get("gstNumber")));
            info.setCompanyType(stringOrEmpty(regPayload.get("companyType")));
            info.setCountryCode(stringOrEmpty(regPayload.get("countryCode")));
            info.setMobileNumber(stringOrEmpty(regPayload.get("mobileNumber")));
            info.setSubmittedAt(stringOrEmpty(regPayload.get("submittedAt")));
            detail.setRegistration(info);
        }

        detail.setDocuments(docs);
        detail.setPublishers(new ArrayList<>());
        detail.setPerformance(new SuperAdminManagementResponse.PerformanceSummary());

        return detail;
    }

    private String stringOrEmpty(Object value) {
        if (value == null) return "";
        String s = String.valueOf(value);
        return s == null ? "" : s;
    }

    private String stringOrNull(Object value) {
        if (value == null) return null;
        return String.valueOf(value);
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    public SuperAdminManagementResponse.AdminActionResponse approveAdmin(String adminId) {
        // 1. Try finding in Admin Registrations first
        org.jackfruit.keliri.model.AdminRegistration reg = registrationRepository.findById(adminId).orElse(null);
        if (reg != null) {
            // Create the active admin user in the local system
            org.jackfruit.keliri.model.users newUser = new org.jackfruit.keliri.model.users();
            newUser.setFullName(reg.getAuthorizedPerson());
            newUser.setEmailAddress(reg.getEmailId());
            newUser.setPassword(reg.getPassword()); // Already encrypted
            newUser.setCompanyName(reg.getCompanyName());
            newUser.setUserType("ADMIN");
            newUser.setAccountStatus("ACTIVE");
            newUser.setGivendor(1);
            newUser.setLatitude(0);
            newUser.setLongitude(0);

            usersRepository.save(newUser);

            // Mark registration as approved
            reg.setStatus("APPROVED");
            reg.setProcessedAt(Instant.now());
            registrationRepository.save(reg);

            // We could also delete it, but keeping it as APPROVED is better for auditing.
            // registrationRepository.delete(reg);

            return applyAdminAction(adminId, "Active", null, "Admin registration approved locally",
                    "Your registration has been approved. You can now login as an Admin.", "Approval");
        }

        // 2. Find company in Mobilize DB directly (Unified Discovery)
        Map<String, Object> company = (Map<String, Object>) (Map) mobilizeApiService.fetchAllCompaniesDirectly()
                .stream()
                .filter(c -> Objects.equals(String.valueOf(c.get("uid")), adminId)
                        || Objects.equals(String.valueOf(c.get("_id")), adminId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Company registration not found in Unified Mobilize DB"));

        boolean success = mobilizeApiService.updateCompanyStatusByCompanyDoc(company, true);
        if (!success) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to approve company in Mobilize API");
        }

        // Provision/update the local admin user used by /api/admin/login.
        Map<String, Object> regPayload = company.get("keliriRegistration") instanceof Map
                ? (Map<String, Object>) company.get("keliriRegistration")
                : Map.of();
        String email = firstNonBlank(
                stringOrNull(regPayload.get("email")),
                stringOrNull(regPayload.get("emailId")),
                stringOrNull(company.get("email")));
        if (email != null) {
            users user = usersRepository.findByEmailAddress(email).orElseGet(users::new);
            if (user.getId() == null || user.getId().isBlank()) {
                user.setId(adminId);
            }
            user.setFullName(firstNonBlank(
                    stringOrNull(regPayload.get("authorizedPerson")),
                    stringOrNull(company.get("name")),
                    "Admin"));
            user.setEmailAddress(email);
            user.setCompanyName(firstNonBlank(
                    stringOrNull(regPayload.get("companyName")),
                    stringOrNull(company.get("name")),
                    ""));
            user.setUserType("ADMIN");
            user.setAccountStatus("ACTIVE");
            user.setGivendor(1);
            user.setLatitude(0);
            user.setLongitude(0);

            String passwordHash = stringOrNull(regPayload.get("passwordHash"));
            if (passwordHash != null && !passwordHash.isBlank()) {
                user.setPassword(passwordHash);
            }

            String mobile = stringOrNull(regPayload.get("mobileNumber"));
            if (mobile != null && !mobile.isBlank()) {
                org.jackfruit.keliri.model.phoneNumber phone = new org.jackfruit.keliri.model.phoneNumber();
                phone.setCountryCode(firstNonBlank(stringOrNull(regPayload.get("countryCode")), "+91"));
                phone.setDialNumber(mobile);
                user.setPhoneNumber(phone);
            }

            usersRepository.save(user);
        }

        return applyAdminAction(adminId, "Active", null, "Admin registration approved",
                "Your company registration has been approved. You can now login to the portal.", "Approval");
    }

    public SuperAdminManagementResponse.AdminActionResponse rejectAdmin(String adminId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reason is mandatory for rejection");
        }

        // For rejection, we currently keep it as status: false in Mobilize,
        // but we can add a 'rejectionReason' field if we modify the model further.
        // For now, we'll just log the action and notify.

        return applyAdminAction(
                adminId,
                "Rejected",
                reason,
                "Admin registration rejected",
                "Rejection notice delivered with reason: " + reason.trim(),
                "Approval");
    }

    public SuperAdminManagementResponse.AdminActionResponse suspendAdmin(String adminId) {
        // 1) Prefer local DB users (real active admins)
        users user = usersRepository.findById(adminId).orElse(null);
        if (user != null) {
            user.setAccountStatus("SUSPENDED");
            usersRepository.save(user);
            return applyAdminAction(adminId, "Suspended", null, null, null, "Account");
        }

        // 2) If adminId comes from Mobilize "companies" dataset, suspend via Mobilize API
        Map<String, Object> company = (Map<String, Object>) (Map) mobilizeApiService.fetchAllCompaniesDirectly()
                .stream()
                .filter(c -> Objects.equals(String.valueOf(c.get("uid")), adminId)
                        || Objects.equals(String.valueOf(c.get("_id")), adminId))
                .findFirst()
                .orElse(null);

        if (company != null) {
            boolean success = mobilizeApiService.updateCompanyStatusByCompanyDoc(company, false);
            if (!success) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Failed to suspend company in Mobilize");
            }
            return applyAdminAction(adminId, "Suspended", null, null, null, "Account");
        }

        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Admin not found");
    }

    public SuperAdminManagementResponse.AdminActionResponse reinstateAdmin(String adminId) {
        // 1) Prefer local DB users (real active admins)
        users user = usersRepository.findById(adminId).orElse(null);
        if (user != null) {
            user.setAccountStatus("ACTIVE");
            usersRepository.save(user);
            return applyAdminAction(adminId, "Active", null, null, null, "Account");
        }

        // 2) If adminId comes from Mobilize "companies" dataset, reinstate via Mobilize API
        Map<String, Object> company = (Map<String, Object>) (Map) mobilizeApiService.fetchAllCompaniesDirectly()
                .stream()
                .filter(c -> Objects.equals(String.valueOf(c.get("uid")), adminId)
                        || Objects.equals(String.valueOf(c.get("_id")), adminId))
                .findFirst()
                .orElse(null);

        if (company != null) {
            boolean success = mobilizeApiService.updateCompanyStatusByCompanyDoc(company, true);
            if (!success) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Failed to reinstate company in Mobilize");
            }
            return applyAdminAction(adminId, "Active", null, null, null, "Account");
        }

        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Admin not found");
    }

    public void deleteAdmin(String adminId) {
        // Try finding in active users
        users user = usersRepository.findById(adminId).orElse(null);
        if (user != null) {
            System.out.println("Deleting Admin User: " + user.getEmailAddress() + " (ID: " + adminId + ")");
            usersRepository.delete(user);

            // Also cleanup registration if it exists for this email
            registrationRepository.findByEmailId(user.getEmailAddress()).ifPresent(reg -> {
                System.out.println("Cleaning up registration for: " + user.getEmailAddress());
                registrationRepository.delete(reg);
            });

            addAuditLog("Super Admin", "Super Admin", "Account Deletion", "User", adminId,
                    "Permanently deleted admin account", "192.168.1.20");
            return;
        }

        // Try finding in registrations only
        org.jackfruit.keliri.model.AdminRegistration reg = registrationRepository.findById(adminId)
                .orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Admin or Registration not found"));

        System.out.println("Deleting Registration: " + reg.getEmailId() + " (ID: " + adminId + ")");
        registrationRepository.delete(reg);
        addAuditLog("Super Admin", "Super Admin", "Registration Deletion", "Registration", adminId,
                "Permanently deleted pending registration", "192.168.1.20");
    }

    public List<SuperAdminManagementResponse.EmailNotificationRecord> getEmailNotifications() {
        return emailNotifications.stream()
                .sorted(Comparator.comparing(SuperAdminManagementResponse.EmailNotificationRecord::getTimestamp)
                        .reversed())
                .limit(20)
                .toList();
    }

    public List<SuperAdminManagementResponse.PublisherRecord> getPublishers(
            String adminId,
            String status,
            String location,
            String search) {
        List<SuperAdminManagementResponse.AdminRecord> admins = getAdmins(null, null);

        List<org.jackfruit.keliri.model.Publisher> publishers = publisherRepository.findAll();
        List<ad_campaigns> campaigns = campaignsRepository.findAll();
        Map<String, List<ad_campaigns>> campaignsByPublisher = campaigns.stream()
                .collect(Collectors.groupingBy(this::resolvePublisherIdForCampaign));

        List<SuperAdminManagementResponse.PublisherRecord> records = new ArrayList<>();
        for (org.jackfruit.keliri.model.Publisher publisher : publishers) {
            SuperAdminManagementResponse.AdminRecord assignedAdmin = admins.stream()
                    .filter(a -> a.getId().equals(publisher.getAdminId()))
                    .findFirst()
                    .orElse(null);

            List<ad_campaigns> publisherCampaigns = campaignsByPublisher.getOrDefault(publisher.getId(), List.of());
            long adsPosted = publisherCampaigns.size();
            long impressions = adsPosted == 0 ? 0 : adsPosted * 4200L;
            long clicks = impressions == 0 ? 0 : Math.round(impressions * 0.02);
            double engagement = impressions == 0 ? 0 : roundToTwoDecimals((clicks * 100.0) / impressions);

            SuperAdminManagementResponse.PublisherRecord record = new SuperAdminManagementResponse.PublisherRecord();
            record.setId(publisher.getId());
            record.setName(defaultString(publisher.getName(), "Publisher"));
            record.setAdminId(assignedAdmin != null ? assignedAdmin.getId() : "SYSTEM");
            record.setAdminName(assignedAdmin != null ? assignedAdmin.getName() : "System");
            record.setLocation(defaultString(publisher.getLocation(), "Unknown"));
            record.setAdsPosted(adsPosted);
            record.setImpressions(impressions);
            record.setClicks(clicks);
            record.setEngagement(engagement);

            // Allow status override from new publisher DB, fallback to campaign logic
            if (publisher.getStatus() != null && "INACTIVE".equalsIgnoreCase(publisher.getStatus())) {
                record.setStatus("Inactive");
            } else {
                record.setStatus(resolvePublisherStatus(publisherCampaigns));
            }

            record.setEmail(defaultString(publisher.getEmail(), "not-available@keliri.com"));
            record.setPhone(defaultString(publisher.getMobile(), "N/A"));
            record.setJoinDate(publisher.getCreatedAt() != null
                    ? publisher.getCreatedAt().atZone(ZONE_ID).toLocalDate().format(DATE_FORMATTER)
                    : resolveDateFromObjectId(publisher.getId()));
            records.add(record);
        }

        return records.stream()
                .filter(record -> matchesPublisherFilters(record, adminId, status, location, search))
                .sorted(Comparator.comparing(SuperAdminManagementResponse.PublisherRecord::getJoinDate).reversed())
                .toList();
    }

    public SuperAdminManagementResponse.PublisherDetail getPublisherDetail(String publisherId) {
        SuperAdminManagementResponse.PublisherRecord base = getPublishers(null, null, null, null).stream()
                .filter(record -> record.getId().equals(publisherId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Publisher not found"));

        List<ad_campaigns> campaigns = campaignsRepository.findAll().stream()
                .filter(campaign -> resolvePublisherIdForCampaign(campaign).equals(publisherId))
                .toList();

        Map<String, advertisements> adsById = advertisementsRepository.findDashboardAdsByIds(
                campaigns.stream().map(ad_campaigns::getAdvertisementId).filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(advertisements::getId, ad -> ad));

        SuperAdminManagementResponse.PublisherDetail detail = new SuperAdminManagementResponse.PublisherDetail();
        copyPublisher(base, detail);
        detail.setAds(campaigns.stream().map(campaign -> {
            advertisements ad = adsById.get(campaign.getAdvertisementId());
            SuperAdminManagementResponse.PublisherAdRecord adRecord = new SuperAdminManagementResponse.PublisherAdRecord();
            adRecord.setId(campaign.getId());
            adRecord.setTitle(ad != null && ad.getTitle() != null ? ad.getTitle() : "Untitled Ad");
            adRecord.setType(ad != null && ad.getAdType() != null ? ad.getAdType() : "Banner");
            adRecord.setStatus(normalizeCampaignStatus(campaign.getCompaignsStatus()));
            adRecord.setCtr(calculateCtrForCampaign(campaign));
            return adRecord;
        }).sorted(Comparator.comparing(SuperAdminManagementResponse.PublisherAdRecord::getId).reversed()).toList());
        return detail;
    }

    public List<SuperAdminManagementResponse.AdvertisementRecord> getAdvertisements() {
        Map<String, users> usersById = new HashMap<>();
        usersRepository.findAll().forEach(user -> usersById.put(user.getId(), user));
        usersRepository.findbygivendor().forEach(user -> usersById.put(user.getId(), user));

        Map<String, SuperAdminManagementResponse.PublisherRecord> firstPublisherByAdminId = getPublishers(null, null,
                null, null).stream()
                .collect(Collectors.toMap(
                        SuperAdminManagementResponse.PublisherRecord::getAdminId,
                        publisher -> publisher,
                        (left, right) -> left));

        Map<String, advertisements> adsById = advertisementsRepository.findDashboardAdsByIds(
                campaignsRepository.findAll().stream()
                        .map(ad_campaigns::getAdvertisementId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(advertisements::getId, ad -> ad));

        return campaignsRepository.findAll().stream()
                .map(campaign -> toAdvertisementRecord(campaign, adsById.get(campaign.getAdvertisementId()), usersById,
                        firstPublisherByAdminId))
                .sorted(Comparator.comparing(SuperAdminManagementResponse.AdvertisementRecord::getCreatedDate)
                        .reversed())
                .toList();
    }

    public SuperAdminManagementResponse.AdvertisementRecord suspendAdvertisement(String campaignId) {
        ad_campaigns campaign = campaignsRepository.findById(campaignId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Campaign not found"));

        campaign.setCompaignsStatus("SUSPENDED");
        campaignsRepository.save(campaign);

        addAuditLog(
                "Super Admin",
                "Super Admin",
                "Ad Update",
                "Ad",
                campaignId,
                "Suspended campaign " + shortId(campaignId),
                "192.168.1.20");

        Map<String, users> usersById = new HashMap<>();
        usersRepository.findAll().forEach(user -> usersById.put(user.getId(), user));
        usersRepository.findbygivendor().forEach(user -> usersById.put(user.getId(), user));
        Map<String, SuperAdminManagementResponse.PublisherRecord> firstPublisherByAdminId = getPublishers(null, null,
                null, null).stream()
                .collect(Collectors.toMap(
                        SuperAdminManagementResponse.PublisherRecord::getAdminId,
                        publisher -> publisher,
                        (left, right) -> left));
        advertisements ad = campaign.getAdvertisementId() == null ? null
                : advertisementsRepository.findById(campaign.getAdvertisementId()).orElse(null);
        return toAdvertisementRecord(campaign, ad, usersById, firstPublisherByAdminId);
    }

    public List<SuperAdminManagementResponse.AuditLogRecord> getAuditLogs(
            String search,
            String actionType,
            String actorRole,
            String entityType,
            String fromDate,
            String toDate) {
        // List<SuperAdminManagementResponse.AuditLogRecord> generated =
        // buildGeneratedAuditLogs();
        List<SuperAdminManagementResponse.AuditLogRecord> allLogs = new ArrayList<>();
        // allLogs.addAll(generated);
        allLogs.addAll(actionAuditLogs);

        LocalDate from = parseDate(fromDate);
        LocalDate to = parseDate(toDate);

        return allLogs.stream()
                .filter(log -> matchesAuditFilters(log, search, actionType, actorRole, entityType, from, to))
                .sorted(Comparator.comparing(SuperAdminManagementResponse.AuditLogRecord::getTimestamp).reversed())
                .toList();
    }

    public List<SuperAdminManagementResponse.TransactionRecord> getTransactions() {
        List<SuperAdminManagementResponse.TransactionRecord> records = new ArrayList<>();

        // 1. Fetch from Local Database
        List<PaymentTransaction> localTxns = paymentTransactionRepository.findAll();
        Map<String, String> adminNames = getAdmins(null, null).stream()
                .collect(Collectors.toMap(SuperAdminManagementResponse.AdminRecord::getId,
                        SuperAdminManagementResponse.AdminRecord::getName, (a, b) -> a));

        for (PaymentTransaction txn : localTxns) {
            SuperAdminManagementResponse.TransactionRecord record = new SuperAdminManagementResponse.TransactionRecord();
            record.setId("LXN-" + shortId(txn.getId()));
            record.setDate(txn.getCreatedAt().toString());
            record.setAdminId(txn.getAdminId());
            record.setAdminName(adminNames.getOrDefault(txn.getAdminId(), "Admin"));
            record.setReference(
                    txn.getRazorpayPaymentId() != null ? txn.getRazorpayPaymentId() : txn.getRazorpayOrderId());
            record.setAmount(txn.getAmount());
            record.setStatus(normalizeStatus(txn.getStatus()));
            record.setIncoming(true);
            records.add(record);
        }

        // 2. Fetch from Razorpay API (for full history/sync)
        try {
            org.json.JSONObject fetchOptions = new org.json.JSONObject();
            fetchOptions.put("count", 100);
            List<Payment> payments = razorpayClient.payments.fetchAll(fetchOptions);
            for (Payment payment : payments) {
                String paymentId = payment.get("id");
                // Avoid duplicates by checking if paymentId is already in records
                if (records.stream().anyMatch(r -> paymentId.equals(r.getReference()))) {
                    continue;
                }

                SuperAdminManagementResponse.TransactionRecord record = new SuperAdminManagementResponse.TransactionRecord();
                record.setId("RXN-" + paymentId.substring(Math.max(0, paymentId.length() - 8)));

                // Razorpay created_at can be Integer (epoch seconds) or Date depending on SDK
                // version
                try {
                    Object createdAtRaw = payment.get("created_at");
                    Instant createdInstant;
                    if (createdAtRaw instanceof java.util.Date) {
                        createdInstant = ((java.util.Date) createdAtRaw).toInstant();
                    } else {
                        long epochSeconds = Long.parseLong(createdAtRaw.toString());
                        createdInstant = Instant.ofEpochSecond(epochSeconds);
                    }
                    record.setDate(createdInstant.toString());
                } catch (Exception e) {
                    record.setDate(Instant.now().toString());
                }

                record.setAdminId("RAZORPAY");
                record.setAdminName("External");
                record.setReference(paymentId);

                // amount is in paise, convert to rupees
                try {
                    Object amountRaw = payment.get("amount");
                    double amount = Double.parseDouble(amountRaw.toString()) / 100.0;
                    record.setAmount(amount);
                } catch (Exception e) {
                    record.setAmount(0);
                }

                try {
                    String status = payment.get("status").toString();
                    record.setStatus(status.substring(0, 1).toUpperCase() + status.substring(1).toLowerCase());
                } catch (Exception e) {
                    record.setStatus("Unknown");
                }
                record.setIncoming(true);
                records.add(record);
            }
        } catch (RazorpayException e) {
            System.err.println("Failed to fetch Razorpay payments: " + e.getMessage());
        }

        return records.stream()
                .sorted(Comparator.comparing(SuperAdminManagementResponse.TransactionRecord::getDate).reversed())
                .toList();
    }

    private String normalizeStatus(String status) {
        if ("SUCCESS".equalsIgnoreCase(status))
            return "Completed";
        if ("FAILED".equalsIgnoreCase(status))
            return "Failed";
        return "Pending";
    }

    private SuperAdminManagementResponse.AdminActionResponse applyAdminAction(
            String adminId,
            String newStatus,
            String reason,
            String emailTrigger,
            String emailContent,
            String actionType) {
        SuperAdminManagementResponse.AdminRecord admin = getAdmins(null, null).stream()
                .filter(record -> record.getId().equals(adminId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Admin not found"));

        SuperAdminManagementResponse.EmailNotificationRecord emailNotification = null;
        if (emailTrigger != null) {
            emailNotification = new SuperAdminManagementResponse.EmailNotificationRecord();
            emailNotification.setId("MAIL-" + Instant.now().toEpochMilli());
            emailNotification.setTrigger(emailTrigger);
            emailNotification.setTo(admin.getEmail());
            emailNotification.setContent(emailContent);
            emailNotification.setTimestamp(Instant.now().toString());
            emailNotifications.add(0, emailNotification);
            if (emailNotifications.size() > 100) {
                emailNotifications.remove(emailNotifications.size() - 1);
            }
        }

        SuperAdminManagementResponse.AdminRecord updated = getAdmins(null, null).stream()
                .filter(record -> record.getId().equals(adminId))
                .findFirst()
                .orElse(admin);

        addAuditLog(
                "Super Admin",
                "Super Admin",
                actionType,
                "Account",
                adminId,
                "Updated admin status to " + newStatus + (reason != null ? " (Reason: " + reason + ")" : ""),
                "192.168.1.20");

        SuperAdminManagementResponse.AdminActionResponse response = new SuperAdminManagementResponse.AdminActionResponse();
        response.setAdmin(updated);
        response.setEmailNotification(emailNotification);
        return response;
    }

    private List<SuperAdminManagementResponse.DocumentItem> buildDocuments(String adminId) {
        SuperAdminManagementResponse.DocumentItem gst = new SuperAdminManagementResponse.DocumentItem();
        gst.setName("GST-Certificate-" + shortId(adminId) + ".pdf");
        gst.setType("GST");
        gst.setUrl("/documents/gst/" + adminId);

        SuperAdminManagementResponse.DocumentItem company = new SuperAdminManagementResponse.DocumentItem();
        company.setName("Company-Registration-" + shortId(adminId) + ".pdf");
        company.setType("Company");
        company.setUrl("/documents/company/" + adminId);

        return List.of(gst, company);
    }

    private SuperAdminManagementResponse.AdminRecord registrationToAdminRecord(
            org.jackfruit.keliri.model.AdminRegistration reg) {
        SuperAdminManagementResponse.AdminRecord record = new SuperAdminManagementResponse.AdminRecord();
        record.setId(reg.getId());
        record.setName(reg.getAuthorizedPerson());
        record.setEmail(reg.getEmailId());
        record.setCompany(reg.getCompanyName());
        record.setRegisteredDate(reg.getSubmittedAt().atZone(ZONE_ID).toLocalDate().format(DATE_FORMATTER));
        record.setStatus(reg.getStatus().substring(0, 1).toUpperCase() + reg.getStatus().substring(1).toLowerCase());
        record.setPhone(reg.getMobileNumber());
        return record;
    }

    private SuperAdminManagementResponse.PerformanceSummary buildPerformance(
            List<SuperAdminManagementResponse.PublisherRecord> linkedPublishers) {
        long totalAds = linkedPublishers.stream().mapToLong(SuperAdminManagementResponse.PublisherRecord::getAdsPosted)
                .sum();
        long totalImpressions = linkedPublishers.stream()
                .mapToLong(SuperAdminManagementResponse.PublisherRecord::getImpressions).sum();
        long totalClicks = linkedPublishers.stream().mapToLong(SuperAdminManagementResponse.PublisherRecord::getClicks)
                .sum();

        SuperAdminManagementResponse.PerformanceSummary performance = new SuperAdminManagementResponse.PerformanceSummary();
        performance.setTotalAds(totalAds);
        performance.setRevenue(totalAds * 1750L);
        performance.setAvgCtr(totalImpressions == 0 ? 0 : roundToTwoDecimals((totalClicks * 100.0) / totalImpressions));
        return performance;
    }

    private SuperAdminManagementResponse.AdminRecord toAdminRecord(users user) {
        SuperAdminManagementResponse.AdminRecord record = new SuperAdminManagementResponse.AdminRecord();
        record.setId(user.getId());
        record.setName(defaultString(user.getFullName(), "Admin"));
        record.setEmail(defaultString(user.getEmailAddress(), "not-available@keliri.com"));
        record.setCompany(resolveCompanyName(user));
        record.setRegisteredDate(resolveDateFromObjectId(user.getId()));
        String rawStatus = defaultString(user.getAccountStatus(), "Active");
        String normalizedStatus = rawStatus.isEmpty() ? "Active" : 
            rawStatus.substring(0, 1).toUpperCase(java.util.Locale.ENGLISH) + rawStatus.substring(1).toLowerCase(java.util.Locale.ENGLISH);
        record.setStatus(normalizedStatus);
        record.setPhone(resolvePhone(user));
        return record;
    }

    private boolean matchesAdminFilters(SuperAdminManagementResponse.AdminRecord admin, String search, String status) {
        if (status != null && !status.isBlank() && !admin.getStatus().equalsIgnoreCase(status.trim())) {
            return false;
        }

        if (search == null || search.isBlank()) {
            return true;
        }

        String query = search.toLowerCase(Locale.ENGLISH);
        return admin.getName().toLowerCase(Locale.ENGLISH).contains(query)
                || admin.getEmail().toLowerCase(Locale.ENGLISH).contains(query)
                || admin.getCompany().toLowerCase(Locale.ENGLISH).contains(query);
    }

    private boolean matchesPublisherFilters(
            SuperAdminManagementResponse.PublisherRecord publisher,
            String adminId,
            String status,
            String location,
            String search) {
        if (adminId != null && !adminId.isBlank() && !adminId.equals(publisher.getAdminId())) {
            return false;
        }

        if (status != null && !status.isBlank() && !status.equalsIgnoreCase(publisher.getStatus())) {
            return false;
        }

        if (location != null && !location.isBlank() && !location.equalsIgnoreCase(publisher.getLocation())) {
            return false;
        }

        if (search == null || search.isBlank()) {
            return true;
        }

        String query = search.toLowerCase(Locale.ENGLISH);
        return publisher.getName().toLowerCase(Locale.ENGLISH).contains(query)
                || publisher.getEmail().toLowerCase(Locale.ENGLISH).contains(query)
                || publisher.getLocation().toLowerCase(Locale.ENGLISH).contains(query);
    }

    private boolean matchesAuditFilters(
            SuperAdminManagementResponse.AuditLogRecord log,
            String search,
            String actionType,
            String actorRole,
            String entityType,
            LocalDate from,
            LocalDate to) {
        if (actionType != null && !actionType.isBlank() && !actionType.equalsIgnoreCase(log.getActionType())) {
            return false;
        }

        if (actorRole != null && !actorRole.isBlank() && !actorRole.equalsIgnoreCase(log.getActorRole())) {
            return false;
        }

        if (entityType != null && !entityType.isBlank() && !entityType.equalsIgnoreCase(log.getEntityType())) {
            return false;
        }

        LocalDate date = Instant.parse(log.getTimestamp()).atZone(ZONE_ID).toLocalDate();
        if (from != null && date.isBefore(from)) {
            return false;
        }

        if (to != null && date.isAfter(to)) {
            return false;
        }

        if (search == null || search.isBlank()) {
            return true;
        }

        String query = search.toLowerCase(Locale.ENGLISH);
        return log.getId().toLowerCase(Locale.ENGLISH).contains(query)
                || log.getActorName().toLowerCase(Locale.ENGLISH).contains(query)
                || log.getAction().toLowerCase(Locale.ENGLISH).contains(query)
                || log.getEntityId().toLowerCase(Locale.ENGLISH).contains(query)
                || log.getIp().contains(search);
    }

    private List<SuperAdminManagementResponse.AuditLogRecord> buildGeneratedAuditLogs() {
        List<ad_campaigns> campaigns = campaignsRepository.findAll();
        Map<String, users> usersById = new HashMap<>();
        usersRepository.findAll().forEach(user -> usersById.put(user.getId(), user));
        usersRepository.findbygivendor().forEach(user -> usersById.put(user.getId(), user));

        List<SuperAdminManagementResponse.AuditLogRecord> logs = new ArrayList<>();
        int index = 0;
        for (ad_campaigns campaign : campaigns.stream().limit(80).toList()) {
            SuperAdminManagementResponse.AuditLogRecord log = new SuperAdminManagementResponse.AuditLogRecord();
            log.setId("LOG-" + (9100 + index));
            log.setTimestamp(resolveCampaignInstant(campaign).toString());
            users actor = usersById.get(campaign.getCreatedBy());
            log.setActorName(actor != null ? defaultString(actor.getFullName(), "Admin") : "Admin User");
            log.setActorRole("Admin");
            log.setActionType(index % 2 == 0 ? "Ad Creation" : "Ad Update");
            log.setEntityType("Ad");
            log.setEntityId(defaultString(campaign.getAdvertisementId(), campaign.getId()));
            log.setAction((index % 2 == 0 ? "Created" : "Updated") + " campaign " + shortId(campaign.getId()));
            log.setIp("10.0." + (index % 8) + "." + ((index % 200) + 11));
            logs.add(log);
            index++;
        }

        List<SuperAdminManagementResponse.AdminRecord> admins = getAdmins(null, null);
        for (int i = 0; i < Math.min(10, admins.size()); i++) {
            SuperAdminManagementResponse.AdminRecord admin = admins.get(i);
            SuperAdminManagementResponse.AuditLogRecord loginLog = new SuperAdminManagementResponse.AuditLogRecord();
            loginLog.setId("LOG-L" + (700 + i));
            loginLog.setTimestamp(Instant.now().minusSeconds((long) i * 7200).toString());
            loginLog.setActorName(admin.getName());
            loginLog.setActorRole("Admin");
            loginLog.setActionType("Login");
            loginLog.setEntityType("Session");
            loginLog.setEntityId("SES-" + shortId(admin.getId()));
            loginLog.setAction("Admin login successful");
            loginLog.setIp("172.16.1." + (i + 10));
            logs.add(loginLog);
        }

        return logs;
    }

    private void addAuditLog(
            String actorName,
            String actorRole,
            String actionType,
            String entityType,
            String entityId,
            String action,
            String ip) {
        SuperAdminManagementResponse.AuditLogRecord record = new SuperAdminManagementResponse.AuditLogRecord();
        record.setId("LOG-A" + Instant.now().toEpochMilli());
        record.setTimestamp(Instant.now().toString());
        record.setActorName(actorName);
        record.setActorRole(actorRole);
        record.setActionType(actionType);
        record.setEntityType(entityType);
        record.setEntityId(entityId);
        record.setAction(action);
        record.setIp(ip);
        actionAuditLogs.add(0, record);
        if (actionAuditLogs.size() > 1000) {
            actionAuditLogs.remove(actionAuditLogs.size() - 1);
        }
    }

    private String resolvePublisherStatus(List<ad_campaigns> campaigns) {
        if (campaigns.isEmpty()) {
            return "Inactive";
        }

        boolean hasActive = campaigns.stream()
                .anyMatch(campaign -> "ACTIVE".equalsIgnoreCase(campaign.getCompaignsStatus()));
        boolean hasPaused = campaigns.stream()
                .anyMatch(campaign -> "SUSPENDED".equalsIgnoreCase(campaign.getCompaignsStatus()));

        if (hasPaused && !hasActive) {
            return "Suspended";
        }

        if (hasActive) {
            return "Active";
        }

        return "Inactive";
    }

    private String resolvePublisherIdForCampaign(ad_campaigns campaign) {
        if (campaign.getCreatedBy() != null && !campaign.getCreatedBy().isBlank()) {
            return campaign.getCreatedBy();
        }
        return "UNKNOWN";
    }

    private SuperAdminManagementResponse.AdvertisementRecord toAdvertisementRecord(
            ad_campaigns campaign,
            advertisements ad,
            Map<String, users> usersById,
            Map<String, SuperAdminManagementResponse.PublisherRecord> firstPublisherByAdminId) {
        SuperAdminManagementResponse.AdvertisementRecord record = new SuperAdminManagementResponse.AdvertisementRecord();
        users admin = usersById.get(campaign.getCreatedBy());
        SuperAdminManagementResponse.PublisherRecord publisher = firstPublisherByAdminId.get(campaign.getCreatedBy());
        Instant campaignInstant = resolveCampaignInstant(campaign);

        record.setId(campaign.getId());
        record.setTitle(ad != null && ad.getTitle() != null ? ad.getTitle() : "Untitled Campaign");
        record.setDescription(ad != null ? ad.getDescription() : null);
        record.setType(ad != null && ad.getAdType() != null ? ad.getAdType() : "Banner");
        record.setAdminId(campaign.getCreatedBy());
        record.setAdminName(admin != null ? defaultString(admin.getFullName(), "Admin") : "Admin User");
        record.setPublisherId(publisher != null ? publisher.getId() : campaign.getCreatedBy());
        record.setPublisherName(publisher != null ? publisher.getName() : "Publisher Network");
        record.setCreatedDate(campaignInstant.atZone(ZONE_ID).toLocalDate().format(DATE_FORMATTER));
        record.setStatus(normalizeCampaignStatus(campaign.getCompaignsStatus()));
        long impressions = hitRecordRepository.countByCampaignIdAndEventType(campaign.getId(), "AD_VIEW");
        long clicks = hitRecordRepository.countByCampaignIdAndEventType(campaign.getId(), "AD_CLICK");
        record.setImpressions(impressions);
        record.setClicks(clicks);
        record.setCtr(impressions == 0 ? 0D : roundToTwoDecimals((clicks * 100.0) / impressions));
        record.setStartDate(resolveStartDate(campaign));
        record.setEndDate(resolveEndDate(campaign));
        record.setLocation(resolveCampaignLocation(campaign));
        record.setRadius(resolveCampaignRadius(campaign));
        record.setImage(resolveCampaignImage(ad));
        return record;
    }

    private String resolveLocationLabel(users publisher, Map<String, txn_user_locations> locationsById) {
        if (publisher.getLastKnownLocation() != null) {
            txn_user_locations location = locationsById.get(publisher.getLastKnownLocation());
            if (location != null && location.getLocation() != null) {
                return String.format(Locale.ENGLISH, "%.4f, %.4f", location.getLocation().getY(),
                        location.getLocation().getX());
            }
        }

        if (publisher.getLatitude() != 0 || publisher.getLongitude() != 0) {
            return String.format(Locale.ENGLISH, "%.4f, %.4f", publisher.getLatitude(), publisher.getLongitude());
        }

        return "Unknown";
    }

    private String resolveCampaignLocation(ad_campaigns campaign) {
        if (campaign.getLocation() == null) {
            return "Unknown";
        }

        if (campaign.getLocation().getLocationName() != null && !campaign.getLocation().getLocationName().isBlank()) {
            return campaign.getLocation().getLocationName();
        }

        return String.format(Locale.ENGLISH, "%.4f, %.4f", campaign.getLocation().getLat(),
                campaign.getLocation().getLng());
    }

    private String resolveCampaignRadius(ad_campaigns campaign) {
        if (campaign.getLocation() == null || campaign.getLocation().getRange() <= 0) {
            return "N/A";
        }

        return roundToTwoDecimals(campaign.getLocation().getRange()) + "km";
    }

    private String resolveStartDate(ad_campaigns campaign) {
        if (campaign.getDateRange() != null && campaign.getDateRange().getFromDate() != null) {
            return campaign.getDateRange().getFromDate().toInstant().atZone(ZONE_ID).toLocalDate()
                    .format(DATE_FORMATTER);
        }

        return resolveDateFromObjectId(campaign.getId());
    }

    private String resolveEndDate(ad_campaigns campaign) {
        if (campaign.getDateRange() != null && campaign.getDateRange().getToDate() != null) {
            return campaign.getDateRange().getToDate().toInstant().atZone(ZONE_ID).toLocalDate().format(DATE_FORMATTER);
        }

        return resolveStartDate(campaign);
    }

    private String resolveCampaignImage(advertisements ad) {
        if (ad == null) {
            return null;
        }

        if (ad.getThumbnail() != null && !ad.getThumbnail().isBlank()) {
            return ad.getThumbnail();
        }

        if (ad.getContent() != null && ad.getContent().getBanners() != null
                && !ad.getContent().getBanners().isEmpty()) {
            String banner = ad.getContent().getBanners().get(0);
            if (banner != null && !banner.isBlank()) {
                return banner;
            }
        }

        if (ad.getContent() != null && ad.getContent().getVideoLink() != null
                && !ad.getContent().getVideoLink().isBlank()) {
            return ad.getContent().getVideoLink();
        }

        return null;
    }

    private String resolveDateFromObjectId(String id) {
        try {
            return new ObjectId(id).getDate().toInstant().atZone(ZONE_ID).toLocalDate().format(DATE_FORMATTER);
        } catch (Exception ex) {
            return LocalDate.now().format(DATE_FORMATTER);
        }
    }

    private String resolveCompanyName(users user) {
        if (user.getEmailAddress() == null || !user.getEmailAddress().contains("@")) {
            return "Keliri Partner";
        }

        String domain = user.getEmailAddress().split("@", 2)[1];
        String root = domain.contains(".") ? domain.substring(0, domain.indexOf('.')) : domain;
        if (root.isBlank()) {
            return "Keliri Partner";
        }

        return Character.toUpperCase(root.charAt(0)) + root.substring(1).toLowerCase(Locale.ENGLISH) + " Media";
    }

    private String resolvePhone(users user) {
        if (user.getPhoneNumber() == null) {
            return "N/A";
        }

        String countryCode = defaultString(user.getPhoneNumber().getCountryCode(), "");
        String dial = defaultString(user.getPhoneNumber().getDialNumber(), "");
        String value = (countryCode + " " + dial).trim();
        return value.isBlank() ? "N/A" : value;
    }

    private Instant resolveCampaignInstant(ad_campaigns campaign) {
        try {
            if (campaign.getDateRange() != null && campaign.getDateRange().getFromDate() != null) {
                return campaign.getDateRange().getFromDate().toInstant();
            }
            return new ObjectId(campaign.getId()).getDate().toInstant();
        } catch (Exception ex) {
            return Instant.now();
        }
    }

    private LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        try {
            return LocalDate.parse(raw, DATE_FORMATTER);
        } catch (Exception ex) {
            return null;
        }
    }

    private double calculateCtrForCampaign(ad_campaigns campaign) {
        int hash = Math.abs(campaign.getId().hashCode() % 300);
        return roundToTwoDecimals(1.5 + (hash / 100.0));
    }

    private String normalizeCampaignStatus(String status) {
        if (status == null || status.isBlank()) {
            return "Draft";
        }

        String normalized = status.trim().toUpperCase(Locale.ENGLISH);
        if ("ACTIVE".equals(normalized)) {
            return "Active";
        }

        if ("SUSPENDED".equals(normalized)) {
            return "Suspended";
        }

        if ("COMPLETED".equals(normalized) || "EXPIRED".equals(normalized)) {
            return "Expired";
        }

        return "Draft";
    }

    private double roundToTwoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private SuperAdminManagementResponse.DocumentItem newDocument(String name, String type, String url) {
        SuperAdminManagementResponse.DocumentItem doc = new SuperAdminManagementResponse.DocumentItem();
        doc.setName(name);
        doc.setType(type);
        doc.setUrl(url);
        return doc;
    }

    private SuperAdminManagementResponse.AdminRecord toAdminRecordFromReg(
            org.jackfruit.keliri.model.AdminRegistration reg) {
        SuperAdminManagementResponse.AdminRecord record = new SuperAdminManagementResponse.AdminRecord();
        record.setId(reg.getId());
        record.setName(reg.getAuthorizedPerson());
        record.setEmail(reg.getEmailId());
        record.setCompany(reg.getCompanyName());
        record.setPhone(reg.getMobileNumber());

        String regStatus = reg.getStatus();
        if ("APPROVED".equalsIgnoreCase(regStatus))
            record.setStatus("Active");
        else if ("REJECTED".equalsIgnoreCase(regStatus))
            record.setStatus("Rejected");
        else
            record.setStatus("Pending");

        if (reg.getSubmittedAt() != null) {
            record.setRegisteredDate(reg.getSubmittedAt().atZone(ZONE_ID).toLocalDate().format(DATE_FORMATTER));
        } else {
            record.setRegisteredDate(LocalDate.now().format(DATE_FORMATTER));
        }
        return record;
    }

    private SuperAdminManagementResponse.AdminRecord mobilizeCompanyToAdminRecord(Map<String, Object> company) {
        SuperAdminManagementResponse.AdminRecord record = new SuperAdminManagementResponse.AdminRecord();

        String id = stringOrNull(company.get("_id"));
        if (id == null)
            id = stringOrNull(company.get("uid"));
        record.setId(id);

        Map primaryContact = (Map) company.get("primaryContact");
        if (primaryContact != null && primaryContact.get("name") != null) {
            record.setName(primaryContact.get("name").toString());
        } else {
            record.setName((String) company.get("name"));
        }

        record.setEmail((String) company.get("email"));
        record.setCompany((String) company.get("name"));

        Object createdAt = company.get("createdAt");
        if (createdAt != null) {
            try {
                String dateStr = createdAt.toString();
                record.setRegisteredDate(dateStr.contains("T") ? dateStr.substring(0, 10) : dateStr);
            } catch (Exception e) {
                record.setRegisteredDate(LocalDate.now().format(DATE_FORMATTER));
            }
        } else {
            record.setRegisteredDate(LocalDate.now().format(DATE_FORMATTER));
        }

        Object statusObj = company.get("status");
        boolean isActive = statusObj instanceof Boolean ? (Boolean) statusObj
                : Boolean.parseBoolean(statusObj.toString());
        // Since we are falling back to the mobilize collection for admins that don't have
        // local user accounts yet, their admin portal status is always "Pending", 
        // regardless of whether their ad company status is active or not.
        record.setStatus("Pending");

        if (primaryContact != null && primaryContact.get("phoneNumber") != null) {
            Map phone = (Map) primaryContact.get("phoneNumber");
            if (phone.get("dialNumber") != null)
                record.setPhone(phone.get("dialNumber").toString());
        } else if (company.get("phoneNumber") != null) {
            Map phone = (Map) company.get("phoneNumber");
            if (phone.get("dialNumber") != null)
                record.setPhone(phone.get("dialNumber").toString());
        }

        return record;
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String shortId(String value) {
        if (value == null || value.length() <= 6) {
            return value == null ? "000000" : value;
        }
        return value.substring(value.length() - 6);
    }

    private void copyAdmin(SuperAdminManagementResponse.AdminRecord source,
            SuperAdminManagementResponse.AdminDetail target) {
        target.setId(source.getId());
        target.setName(source.getName());
        target.setEmail(source.getEmail());
        target.setCompany(source.getCompany());
        target.setRegisteredDate(source.getRegisteredDate());
        target.setStatus(source.getStatus());
        target.setPhone(source.getPhone());
    }

    private void copyPublisher(SuperAdminManagementResponse.PublisherRecord source,
            SuperAdminManagementResponse.PublisherDetail target) {
        target.setId(source.getId());
        target.setName(source.getName());
        target.setAdminId(source.getAdminId());
        target.setAdminName(source.getAdminName());
        target.setLocation(source.getLocation());
        target.setAdsPosted(source.getAdsPosted());
        target.setImpressions(source.getImpressions());
        target.setClicks(source.getClicks());
        target.setEngagement(source.getEngagement());
        target.setStatus(source.getStatus());
        target.setEmail(source.getEmail());
        target.setPhone(source.getPhone());
        target.setJoinDate(source.getJoinDate());
    }
}
