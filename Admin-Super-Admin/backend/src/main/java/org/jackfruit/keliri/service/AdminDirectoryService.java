package org.jackfruit.keliri.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jackfruit.keliri.model.AdminRegistration;
import org.jackfruit.keliri.model.users;
import org.jackfruit.keliri.repository.AdminRegistrationRepository;
import org.jackfruit.keliri.repository.usersRepository;
import org.springframework.stereotype.Service;

@Service
public class AdminDirectoryService {
    public enum SourceType {
        MOBILIZE,
        LOCAL_USER,
        LOCAL_REGISTRATION,
        NONE
    }

    public static class Resolution {
        private SourceType source = SourceType.NONE;
        private users localUser;
        private AdminRegistration localRegistration;
        private Map<String, Object> mobilizeCompany;
        private final List<String> auditTrail = new ArrayList<>();

        public SourceType getSource() {
            return source;
        }

        public void setSource(SourceType source) {
            this.source = source;
        }

        public users getLocalUser() {
            return localUser;
        }

        public void setLocalUser(users localUser) {
            this.localUser = localUser;
        }

        public AdminRegistration getLocalRegistration() {
            return localRegistration;
        }

        public void setLocalRegistration(AdminRegistration localRegistration) {
            this.localRegistration = localRegistration;
        }

        public Map<String, Object> getMobilizeCompany() {
            return mobilizeCompany;
        }

        public void setMobilizeCompany(Map<String, Object> mobilizeCompany) {
            this.mobilizeCompany = mobilizeCompany;
        }

        public List<String> getAuditTrail() {
            return auditTrail;
        }

        public String getSourceLabel() {
            return switch (source) {
                case MOBILIZE -> "Mobilize";
                case LOCAL_USER -> "Local User";
                case LOCAL_REGISTRATION -> "Local Registration";
                default -> "None";
            };
        }
    }

    private final MobilizeApiService mobilizeApiService;
    private final usersRepository usersRepository;
    private final AdminRegistrationRepository registrationRepository;

    public AdminDirectoryService(
            MobilizeApiService mobilizeApiService,
            usersRepository usersRepository,
            AdminRegistrationRepository registrationRepository) {
        this.mobilizeApiService = mobilizeApiService;
        this.usersRepository = usersRepository;
        this.registrationRepository = registrationRepository;
    }

    public Resolution resolveById(String adminId) {
        Resolution resolution = new Resolution();
        resolution.getAuditTrail().add("Checking Mobilize directory by id");

        Map<String, Object> company = mobilizeApiService.findCompanyDynamic(adminId);
        if (company != null) {
            resolution.setSource(SourceType.MOBILIZE);
            resolution.setMobilizeCompany(company);
            resolution.getAuditTrail().add("Resolved from Mobilize");
            return resolution;
        }

        resolution.getAuditTrail().add("Mobilize miss, checking local users");
        users localUser = usersRepository.findById(adminId).orElse(null);
        if (localUser == null) {
            localUser = usersRepository.findbygivendor().stream()
                    .filter(user -> adminId.equalsIgnoreCase(s(user.getId())))
                    .findFirst()
                    .orElse(null);
        }
        if (localUser != null) {
            resolution.setSource(SourceType.LOCAL_USER);
            resolution.setLocalUser(localUser);
            resolution.getAuditTrail().add("Resolved from local users");
            return resolution;
        }

        resolution.getAuditTrail().add("Local user miss, checking local registrations");
        AdminRegistration registration = registrationRepository.findById(adminId).orElse(null);
        if (registration != null) {
            resolution.setSource(SourceType.LOCAL_REGISTRATION);
            resolution.setLocalRegistration(registration);
            resolution.getAuditTrail().add("Resolved from local registrations");
            return resolution;
        }

        resolution.getAuditTrail().add("No source resolved the admin");
        return resolution;
    }

    public Resolution resolveByEmail(String email) {
        Resolution resolution = new Resolution();
        if (email == null || email.isBlank()) {
            resolution.getAuditTrail().add("Blank email");
            return resolution;
        }

        String normalized = email.trim().toLowerCase(Locale.ENGLISH);
        resolution.getAuditTrail().add("Checking Mobilize directory by email");

        Map<String, Object> company = mobilizeApiService.findCompanyDynamic(normalized);
        if (company != null) {
            resolution.setSource(SourceType.MOBILIZE);
            resolution.setMobilizeCompany(company);
            resolution.getAuditTrail().add("Resolved from Mobilize");
            return resolution;
        }

        resolution.getAuditTrail().add("Mobilize miss, checking local users");
        users localUser = usersRepository.findByEmailAddress(normalized).orElse(null);
        if (localUser != null) {
            resolution.setSource(SourceType.LOCAL_USER);
            resolution.setLocalUser(localUser);
            resolution.getAuditTrail().add("Resolved from local users");
            return resolution;
        }

        resolution.getAuditTrail().add("Local user miss, checking local registrations");
        AdminRegistration registration = registrationRepository.findByEmailId(normalized).orElse(null);
        if (registration != null) {
            resolution.setSource(SourceType.LOCAL_REGISTRATION);
            resolution.setLocalRegistration(registration);
            resolution.getAuditTrail().add("Resolved from local registrations");
            return resolution;
        }

        resolution.getAuditTrail().add("No source resolved the admin");
        return resolution;
    }

    private String s(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
