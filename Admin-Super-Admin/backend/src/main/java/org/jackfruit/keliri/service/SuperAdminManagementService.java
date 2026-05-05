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
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;
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
import org.jackfruit.keliri.repository.AuditLogRepository;
import org.jackfruit.keliri.model.AuditLogEntry;
import com.razorpay.Payment;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@Service
public class SuperAdminManagementService {
    private static final ZoneId ZONE_ID = ZoneId.systemDefault();
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final usersRepository usersRepository;
    private final ad_campaignsRepository campaignsRepository;
    private final advertisementsRepository advertisementsRepository;
    private final txn_user_locationsRepository locationsRepository;
    private final hitRecordRepository hitRecordRepository;
    private final org.jackfruit.keliri.repository.AdminRegistrationRepository registrationRepository;
    private final PublisherRepository publisherRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final RazorpayClient razorpayClient;
    private final MongoTemplate mongoTemplate;
    private final MobilizeApiService mobilizeApiService;
    private final AuditLogRepository auditLogRepository;

    private final List<SuperAdminManagementResponse.AuditLogRecord> actionAuditLogs = new CopyOnWriteArrayList<>();
    private final List<SuperAdminManagementResponse.EmailNotificationRecord> emailNotifications = new CopyOnWriteArrayList<>();

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
            MobilizeApiService mobilizeApiService,
            MongoTemplate mongoTemplate,
            AuditLogRepository auditLogRepository) {
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
        this.mongoTemplate = mongoTemplate;
        this.auditLogRepository = auditLogRepository;
    }

    @PostConstruct
    public void init() {
        if (auditLogRepository.count() == 0) {
            System.out.println(">>> [SEED] Seeding initial audit logs...");
            recordAuditEvent("Ruthvik Master", "MASTER_SUPER_ADMIN", "Login", "Session", "master@keliri.com",
                    "System initialization login", "127.0.0.1", "Seed");
            recordAuditEvent("Ruthvik Master", "MASTER_SUPER_ADMIN", "Approval", "User", "admin_001",
                    "Approved new admin registration", "127.0.0.1", "Seed");
            recordAuditEvent("System", "Internal", "Payment", "Transaction", "PAY_7721", "Processed recurring payout",
                    "0.0.0.0", "Seed");
            recordAuditEvent("Admin User", "Admin", "Ad Creation", "Ad", "CAM_123",
                    "Created new campaign 'Summer Sale'", "192.168.1.1", "Seed");
        }
    }

    public List<SuperAdminManagementResponse.AdminRecord> getAdmins(String search, String status) {
        List<SuperAdminManagementResponse.AdminRecord> admins = new ArrayList<>();
        Criteria adminCriteria = new Criteria().orOperator(
                Criteria.where("userType").is("admin"),
                Criteria.where("userType").is("ADMIN"),
                Criteria.where("role").is("admin"),
                Criteria.where("role").is("ADMIN"));
        Query adminQuery = new Query(adminCriteria);
        List<users> adminUsers = mongoTemplate.find(adminQuery, users.class);

        List<SuperAdminManagementResponse.AdminRecord> activeAdmins = adminUsers.stream()
                .map(this::toAdminRecord)
                .toList();
        admins.addAll(activeAdmins);

        // The EC2 companies are publishers in the Ad ecosystem; they should not be
        // injected into the Super Admin's "Admin Management" list.
        // We only retain admins from the users collection and admin_registrations.

        List<org.jackfruit.keliri.model.AdminRegistration> regs = registrationRepository.findAll();
        for (org.jackfruit.keliri.model.AdminRegistration reg : regs) {
            if (admins.stream().anyMatch(a -> Objects.equals(a.getEmail(), reg.getEmailId())))
                continue;
            admins.add(toAdminRecordFromReg(reg));
        }

        return admins.stream()
                .filter(admin -> matchesAdminFilters(admin, search, status))
                .sorted(Comparator.comparing(SuperAdminManagementResponse.AdminRecord::getRegisteredDate).reversed())
                .toList();
    }

    public SuperAdminManagementResponse.AdminDetail getAdminDetail(String adminId) {
        Criteria adminCriteria = new Criteria().orOperator(
                Criteria.where("userType").is("admin"),
                Criteria.where("userType").is("ADMIN"),
                Criteria.where("role").is("admin"),
                Criteria.where("role").is("ADMIN"));
        Query adminQuery = new Query(adminCriteria);
        List<users> adminUsers = mongoTemplate.find(adminQuery, users.class);

        users admin = adminUsers.stream()
                .filter(user -> Objects.equals(user.getId(), adminId))
                .findFirst()
                .orElse(null);

        if (admin != null) {
            SuperAdminManagementResponse.AdminDetail detail = new SuperAdminManagementResponse.AdminDetail();
            SuperAdminManagementResponse.AdminRecord base = toAdminRecord(admin);
            copyAdmin(base, detail);
            List<SuperAdminManagementResponse.PublisherRecord> pubs = getPublishers(null, null, null, null).stream()
                    .filter(p -> adminId.equals(p.getAdminId())).toList();
            detail.setPublishers(pubs.stream().map(p -> {
                SuperAdminManagementResponse.PublisherMini m = new SuperAdminManagementResponse.PublisherMini();
                m.setId(p.getId());
                m.setName(p.getName());
                m.setStatus(p.getStatus());
                m.setAdsPosted(p.getAdsPosted());
                return m;
            }).toList());
            detail.setDocuments(new ArrayList<>());
            // Identify company in Mobilize to fetch real ad stats
            List<Map<String, Object>> allCompanies = mobilizeApiService.fetchAllCompaniesDirectly();
            String ec2CompanyUid = null;
            for (Map c : allCompanies) {
                if (Objects.equals(c.get("email"), admin.getEmailAddress())) {
                    ec2CompanyUid = c.get("uid") != null ? String.valueOf(c.get("uid")) : String.valueOf(c.get("_id"));
                    break;
                }
            }

            int totalCampaigns = campaignsRepository.findByCreatedBy(adminId).size();

            // Override with actual EC2 campaigns if linked
            if (ec2CompanyUid != null) {
                List<Map<String, Object>> liveAds = mobilizeApiService.fetchCampaignsByCompany(ec2CompanyUid);
                totalCampaigns = liveAds.size();

                // Update publishers' ad counts realistically for this admin's company!
                for (SuperAdminManagementResponse.PublisherMini m : detail.getPublishers()) {
                    long adsForPub = liveAds.stream()
                            .filter(ad -> {
                                Object comp = ad.get("company");
                                if (comp instanceof Map)
                                    return Objects.equals(((Map) comp).get("uid"), m.getId())
                                            || Objects.equals(((Map) comp).get("_id"), m.getId());
                                return Objects.equals(comp, m.getId());
                            }).count();
                    if (adsForPub > 0) {
                        m.setAdsPosted((int) adsForPub);
                        m.setStatus("Active");
                    }
                }
            }

            long totalRevenue = 0;
            List<PaymentTransaction> txns = paymentTransactionRepository.findByAdminIdAndStatus(adminId, "SUCCESS");
            if (txns != null) {
                totalRevenue = txns.stream().mapToLong(t -> Math.round(t.getAmount())).sum();
            }

            SuperAdminManagementResponse.PerformanceSummary perf = new SuperAdminManagementResponse.PerformanceSummary();
            perf.setTotalAds(totalCampaigns);
            perf.setRevenue(totalRevenue);
            perf.setAvgCtr(0); // Mock until Native CTR metric
            detail.setPerformance(perf);
            return detail;
        }

        org.jackfruit.keliri.model.AdminRegistration reg = registrationRepository.findById(adminId).orElse(null);
        if (reg != null) {
            SuperAdminManagementResponse.AdminDetail detail = new SuperAdminManagementResponse.AdminDetail();
            copyAdmin(toAdminRecordFromReg(reg), detail);
            detail.setPublishers(new ArrayList<>());
            detail.setDocuments(new ArrayList<>());
            detail.setPerformance(new SuperAdminManagementResponse.PerformanceSummary());
            return detail;
        }

        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Admin not found");
    }

    public SuperAdminManagementResponse.AdminActionResponse approveAdmin(String adminId, String actorName,
            String actorRole) {
        org.jackfruit.keliri.model.AdminRegistration reg = registrationRepository.findById(adminId).orElse(null);
        if (reg != null) {
            org.jackfruit.keliri.model.users newUser = new org.jackfruit.keliri.model.users();
            newUser.setFullName(reg.getAuthorizedPerson());
            newUser.setEmailAddress(reg.getEmailId());
            newUser.setPassword(reg.getPassword());
            newUser.setCompanyName(reg.getCompanyName());
            newUser.setUserType("ADMIN");
            newUser.setAccountStatus("ACTIVE");
            usersRepository.save(newUser);
            reg.setStatus("APPROVED");
            registrationRepository.save(reg);
            return applyAdminAction(adminId, "Active", null, "Account", null, "Approval", actorName, actorRole);
        }
        return applyAdminAction(adminId, "Active", null, "Account", null, "Approval", actorName, actorRole);
    }

    public SuperAdminManagementResponse.AdminActionResponse rejectAdmin(String adminId, String reason, String actorName,
            String actorRole) {
        return applyAdminAction(adminId, "Rejected", reason, "Account", null, "Rejection", actorName, actorRole);
    }

    public SuperAdminManagementResponse.AdminActionResponse suspendAdmin(String adminId, String actorName,
            String actorRole) {
        users user = usersRepository.findById(adminId).orElse(null);
        if (user != null) {
            user.setAccountStatus("SUSPENDED");
            usersRepository.save(user);
        }
        return applyAdminAction(adminId, "Suspended", null, "Account", null, "Suspension", actorName, actorRole);
    }

    public SuperAdminManagementResponse.AdminActionResponse reinstateAdmin(String adminId, String actorName,
            String actorRole) {
        users user = usersRepository.findById(adminId).orElse(null);
        if (user != null) {
            user.setAccountStatus("ACTIVE");
            usersRepository.save(user);
        }
        return applyAdminAction(adminId, "Active", null, "Account", null, "Reinstatement", actorName, actorRole);
    }

    public void deleteAdmin(String adminId, String actorName, String actorRole) {
        usersRepository.deleteById(adminId);

        recordAuditEvent(
                actorName,
                actorRole,
                "Admin Deletion",
                "Account",
                adminId,
                "Deleted admin account: " + adminId,
                "0.0.0.0",
                "System");
        registrationRepository.deleteById(adminId);
    }

    public List<SuperAdminManagementResponse.EmailNotificationRecord> getEmailNotifications() {
        return emailNotifications;
    }

    public List<SuperAdminManagementResponse.PublisherRecord> getPublishers(String adminId, String status,
            String location, String search) {
        List<org.jackfruit.keliri.model.Publisher> publishers = publisherRepository.findAll();
        List<ad_campaigns> campaigns = campaignsRepository.findAll();
        Map<String, List<ad_campaigns>> campaignsByPub = campaigns.stream()
                .collect(Collectors.groupingBy(this::resolvePublisherIdForCampaign));
        List<SuperAdminManagementResponse.PublisherRecord> records = new ArrayList<>();
        for (org.jackfruit.keliri.model.Publisher p : publishers) {
            List<ad_campaigns> pubCampaigns = campaignsByPub.getOrDefault(p.getId(), List.of());
            SuperAdminManagementResponse.PublisherRecord r = new SuperAdminManagementResponse.PublisherRecord();
            r.setId(p.getId());
            r.setName(p.getName() != null ? p.getName() : "Publisher");
            r.setAdminId(p.getAdminId() != null ? p.getAdminId() : "SYSTEM");
            r.setAdsPosted(pubCampaigns.size());
            r.setStatus(resolvePublisherStatus(pubCampaigns));
            r.setJoinDate(resolveDateFromObjectId(p.getId()));
            records.add(r);
        }
        return records.stream().filter(r -> matchesPublisherFilters(r, adminId, status, location, search)).toList();
    }

    public SuperAdminManagementResponse.PublisherDetail getPublisherDetail(String publisherId) {
        SuperAdminManagementResponse.PublisherRecord base = getPublishers(null, null, null, null).stream()
                .filter(p -> p.getId().equals(publisherId)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        SuperAdminManagementResponse.PublisherDetail detail = new SuperAdminManagementResponse.PublisherDetail();
        copyPublisher(base, detail);
        detail.setAds(new ArrayList<>());
        return detail;
    }

    public List<SuperAdminManagementResponse.AdvertisementRecord> getAdvertisements() {
        Map<String, users> usersById = new HashMap<>();
        usersRepository.findAll().forEach(u -> usersById.put(u.getId(), u));
        Map<String, advertisements> adsById = advertisementsRepository.findAll().stream()
                .collect(Collectors.toMap(advertisements::getId, a -> a));
        return campaignsRepository.findAll().stream()
                .map(c -> toAdvertisementRecord(c, adsById.get(c.getAdvertisementId()), usersById, null))
                .sorted(Comparator.comparing(SuperAdminManagementResponse.AdvertisementRecord::getCreatedDate)
                        .reversed())
                .collect(Collectors.toList());
    }

    public SuperAdminManagementResponse.AdvertisementRecord suspendAdvertisement(String campaignId, String actorName,
            String actorRole) {
        ad_campaigns c = campaignsRepository.findById(campaignId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        c.setCompaignsStatus("SUSPENDED");
        campaignsRepository.save(c);

        recordAuditEvent(
                actorName,
                actorRole,
                "Ad Suspension",
                "Ad",
                campaignId,
                "Suspended ad campaign: " + campaignId,
                "0.0.0.0",
                "System");

        return toAdvertisementRecord(c, null, new HashMap<>(), null);
    }

    public List<SuperAdminManagementResponse.AuditLogRecord> getAuditLogs(String search, String actionType,
            String actorRole, String entityType, String fromDate, String toDate) {
        List<AuditLogEntry> entries = auditLogRepository.findAll();
        List<SuperAdminManagementResponse.AuditLogRecord> records = new ArrayList<>();

        LocalDate start = parseDate(fromDate);
        LocalDate end = parseDate(toDate);

        for (AuditLogEntry entry : entries) {
            SuperAdminManagementResponse.AuditLogRecord r = new SuperAdminManagementResponse.AuditLogRecord();
            r.setId(entry.getId());
            r.setTimestamp(entry.getTimestamp() != null ? entry.getTimestamp().toString() : Instant.now().toString());
            r.setActorName(entry.getActorName());
            r.setActorRole(entry.getActorRole());
            r.setActionType(entry.getActionType());
            r.setEntityType(entry.getEntityType());
            r.setEntityId(entry.getEntityId());
            r.setAction(entry.getAction());
            r.setIp(entry.getIp());

            if (matchesAuditFilters(r, search, actionType, actorRole, entityType, start, end)) {
                records.add(r);
            }
        }

        records.sort((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()));
        return records;
    }

    public List<SuperAdminManagementResponse.TransactionRecord> getTransactions(String actorName, String actorRole) {
        List<PaymentTransaction> transactions = paymentTransactionRepository.findAll();
        List<SuperAdminManagementResponse.TransactionRecord> records = new ArrayList<>();
        Map<String, String> adminNames = new HashMap<>();

        for (PaymentTransaction tx : transactions) {
            SuperAdminManagementResponse.TransactionRecord record = new SuperAdminManagementResponse.TransactionRecord();
            record.setId(tx.getId() != null ? tx.getId() : "TXN-" + System.currentTimeMillis());
            record.setAdminId(tx.getAdminId());

            if (tx.getAdminId() != null) {
                String name = adminNames.computeIfAbsent(tx.getAdminId(), id -> {
                    Optional<users> user = usersRepository.findById(id);
                    return user.isPresent() ? user.get().getFullName() : "Unknown Admin";
                });
                record.setAdminName(name);
            } else {
                record.setAdminName("System");
            }

            record.setAmount(tx.getAmount());

            // Normalize status for UI
            String rawStatus = tx.getStatus();
            if ("SUCCESS".equalsIgnoreCase(rawStatus)) {
                record.setStatus("Completed");
            } else if ("PENDING".equalsIgnoreCase(rawStatus)) {
                record.setStatus("Pending");
            } else {
                record.setStatus("Failed");
            }

            record.setIncoming(true);
            record.setReference(tx.getRazorpayOrderId() != null ? tx.getRazorpayOrderId()
                    : (tx.getCampaignId() != null ? tx.getCampaignId() : "N/A"));

            if (tx.getCreatedAt() != null) {
                record.setDate(tx.getCreatedAt().toString());
            } else {
                record.setDate(Instant.now().toString());
            }

            records.add(record);
        }

        records.sort((a, b) -> {
            if (a.getDate() == null || b.getDate() == null)
                return 0;
            return b.getDate().compareTo(a.getDate());
        });

        recordAuditEvent(
                actorName,
                actorRole,
                "Payment View",
                "Payment",
                "ALL",
                "Viewed payment transaction history",
                "0.0.0.0",
                "System");

        return records;
    }

    // Helpers
    private SuperAdminManagementResponse.AdminRecord toAdminRecord(users user) {
        SuperAdminManagementResponse.AdminRecord r = new SuperAdminManagementResponse.AdminRecord();
        r.setId(user.getId());
        r.setName(user.getFullName());
        r.setEmail(user.getEmailAddress());
        r.setCompany(user.getCompanyName());
        r.setPhone(user.getPhoneNumber() != null ? String.valueOf(user.getPhoneNumber()) : "");
        r.setStatus("SUSPENDED".equalsIgnoreCase(user.getAccountStatus()) ? "Suspended" : "Active");
        r.setRegisteredDate(resolveDateFromObjectId(user.getId()));
        return r;
    }

    private SuperAdminManagementResponse.AdminRecord mobilizeCompanyToAdminRecord(Map<String, Object> company) {
        SuperAdminManagementResponse.AdminRecord r = new SuperAdminManagementResponse.AdminRecord();
        r.setId(String.valueOf(company.get("_id")));
        r.setName(String.valueOf(company.get("name")));
        r.setStatus("Pending");
        r.setRegisteredDate(LocalDate.now().format(DATE_FORMATTER));
        return r;
    }

    private SuperAdminManagementResponse.AdminRecord toAdminRecordFromReg(
            org.jackfruit.keliri.model.AdminRegistration reg) {
        SuperAdminManagementResponse.AdminRecord r = new SuperAdminManagementResponse.AdminRecord();
        r.setId(reg.getId());
        r.setName(reg.getAuthorizedPerson());
        r.setStatus("Pending");
        r.setRegisteredDate(LocalDate.now().format(DATE_FORMATTER));
        return r;
    }

    private SuperAdminManagementResponse.AdvertisementRecord toAdvertisementRecord(ad_campaigns c, advertisements ad,
            Map<String, users> usersById, Map<String, ?> p) {
        SuperAdminManagementResponse.AdvertisementRecord r = new SuperAdminManagementResponse.AdvertisementRecord();
        r.setId(c.getId());
        r.setTitle(ad != null ? ad.getTitle() : "Ad");
        r.setCreatedDate(resolveDateFromObjectId(c.getId()));
        r.setStatus(normalizeCampaignStatus(c.getCompaignsStatus()));
        return r;
    }

    private String normalizeCampaignStatus(String s) {
        return s == null ? "Draft" : ("ACTIVE".equalsIgnoreCase(s) ? "Active" : "Suspended");
    }

    private String resolveDateFromObjectId(String id) {
        try {
            return new ObjectId(id).getDate().toInstant().atZone(ZONE_ID).toLocalDate().format(DATE_FORMATTER);
        } catch (Exception e) {
            return LocalDate.now().format(DATE_FORMATTER);
        }
    }

    private double roundToTwoDecimals(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private SuperAdminManagementResponse.DocumentItem newDocument(String n, String t, String u) {
        SuperAdminManagementResponse.DocumentItem d = new SuperAdminManagementResponse.DocumentItem();
        d.setName(n);
        d.setType(t);
        d.setUrl(u);
        return d;
    }

    private LocalDate parseDate(String r) {
        try {
            return LocalDate.parse(r, DATE_FORMATTER);
        } catch (Exception e) {
            return null;
        }
    }

    private String resolvePublisherStatus(List<ad_campaigns> cs) {
        return cs.stream().anyMatch(c -> "ACTIVE".equalsIgnoreCase(c.getCompaignsStatus())) ? "Active" : "Inactive";
    }

    private String resolvePublisherIdForCampaign(ad_campaigns c) {
        return c.getCompanies() != null ? c.getCompanies().getId() : c.getCreatedBy();
    }

    private void copyAdmin(SuperAdminManagementResponse.AdminRecord s, SuperAdminManagementResponse.AdminDetail t) {
        t.setId(s.getId());
        t.setName(s.getName());
        t.setStatus(s.getStatus());
        t.setRegisteredDate(s.getRegisteredDate());
        t.setEmail(s.getEmail());
        t.setCompany(s.getCompany());
        t.setPhone(s.getPhone());
    }

    private void copyPublisher(SuperAdminManagementResponse.PublisherRecord s,
            SuperAdminManagementResponse.PublisherDetail t) {
        t.setId(s.getId());
        t.setName(s.getName());
        t.setStatus(s.getStatus());
    }

    private boolean matchesAdminFilters(SuperAdminManagementResponse.AdminRecord a, String s, String st) {
        return true;
    }

    private boolean matchesPublisherFilters(SuperAdminManagementResponse.PublisherRecord r, String a, String s,
            String l, String se) {
        return true;
    }

    private boolean matchesAuditFilters(SuperAdminManagementResponse.AuditLogRecord l, String s, String at, String ar,
            String e, LocalDate start, LocalDate end) {
        if (s != null && !s.isBlank()) {
            String lowerS = s.toLowerCase();
            boolean match = (l.getActorName() != null && l.getActorName().toLowerCase().contains(lowerS)) ||
                    (l.getAction() != null && l.getAction().toLowerCase().contains(lowerS)) ||
                    (l.getId() != null && l.getId().toLowerCase().contains(lowerS));
            if (!match)
                return false;
        }
        if (at != null && !at.isBlank() && !at.equalsIgnoreCase(l.getActionType()))
            return false;
        if (ar != null && !ar.isBlank() && !ar.equalsIgnoreCase(l.getActorRole()))
            return false;
        if (e != null && !e.isBlank() && !e.equalsIgnoreCase(l.getEntityType()))
            return false;

        if (l.getTimestamp() != null) {
            LocalDate logDate = Instant.parse(l.getTimestamp()).atZone(ZONE_ID).toLocalDate();
            if (start != null && logDate.isBefore(start))
                return false;
            if (end != null && logDate.isAfter(end))
                return false;
        }

        return true;
    }

    private void addAuditLog(String an, String ar, String at, String et, String ei, String a, String i) {
        recordAuditEvent(an, ar, at, et, ei, a, i, "System");
    }

    private SuperAdminManagementResponse.AdminActionResponse applyAdminAction(String adminId, String newStatus,
            String reason, String entityType,
            String entityCategory, String actionType, String actorName, String actorRole) {
        SuperAdminManagementResponse.AdminActionResponse res = new SuperAdminManagementResponse.AdminActionResponse();
        res.setSuccess(true);
        res.setMessage("Admin status updated to " + newStatus);

        recordAuditEvent(
                actorName != null ? actorName : "Super Admin",
                actorRole != null ? actorRole : "Super Admin",
                actionType != null ? actionType : "Admin Action",
                entityType != null ? entityType : "Account",
                adminId,
                "Updated admin status to " + newStatus + (reason != null ? ". Reason: " + reason : ""),
                "0.0.0.0",
                "System");

        return res;
    }

    public void recordAuditEvent(String an, String ar, String at, String et, String ei, String a, String i, String p) {
        AuditLogEntry entry = new AuditLogEntry();
        entry.setTimestamp(Instant.now());
        entry.setActorName(an);
        entry.setActorRole(ar);
        entry.setActionType(at);
        entry.setEntityType(et);
        entry.setEntityId(ei);
        entry.setAction(a);
        entry.setIp(i);
        entry.setDataSource(p);
        auditLogRepository.save(entry);
    }

    private String shortId(String v) {
        return v != null && v.length() > 6 ? v.substring(v.length() - 6) : v;
    }
}
