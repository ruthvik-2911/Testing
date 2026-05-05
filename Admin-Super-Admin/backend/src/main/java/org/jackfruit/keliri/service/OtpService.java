package org.jackfruit.keliri.service;

import org.jackfruit.keliri.model.mobile_otp;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Random;
import java.util.UUID;

@Service
public class OtpService {

    @Autowired
    private AwsSnsService snsService;

    @Autowired
    private MongoTemplate mongoTemplate;

    /**
     * Send OTP to a phone number and store the session in DB
     */
    public String initiateOtp(String phoneNumber) {
        // 1. Generate 6-digit OTP
        int otp = 100000 + new Random().nextInt(900000);

        // 2. Generate a unique session/hash ID (Mobilize pattern)
        String sessionId = UUID.randomUUID().toString();

        // 3. Save to MongoDB
        mobile_otp otpEntry = new mobile_otp();
        otpEntry.setId(sessionId); // Using UUID as ID (hash/sessionId)
        otpEntry.setMobile_otp_number(phoneNumber);
        otpEntry.setMobile_otp_value(otp);
        otpEntry.setMobile_otp_expiry_time(LocalDateTime.now().plusMinutes(5));
        otpEntry.setMobile_otp_status(0);
        otpEntry.setMobile_otp_reason("Login OTP");

        mongoTemplate.save(otpEntry);

        // 4. Send SMS via AWS SNS
        // This MUST match the DLT template exactly: {#var#} is your one time
        // password(OTP). Please enter the OTP to proceed. Thank you, Team Keliri
        String message = otp
                + " is your one time password(OTP). Please enter the OTP to proceed. Thank you, Team Keliri";
        boolean sent = snsService.sendSms(phoneNumber, message);

        if (!sent) {
            throw new RuntimeException("Failed to send SMS via AWS SNS");
        }

        return sessionId;
    }

    /**
     * Verify OTP using the sessionId (hash)
     */
    public boolean verifyOtp(String sessionId, String otp) {
        try {
            int otpValue = Integer.parseInt(otp);
            Query query = new Query();
            query.addCriteria(Criteria.where("id").is(sessionId));
            query.addCriteria(Criteria.where("mobile_otp_value").is(otpValue));
            query.addCriteria(Criteria.where("mobile_otp_status").is(0));
            query.addCriteria(Criteria.where("mobile_otp_expiry_time").gt(LocalDateTime.now()));

            mobile_otp match = mongoTemplate.findOne(query, mobile_otp.class);

            if (match != null) {
                // Mark as used
                match.setMobile_otp_status(1);
                match.setMobile_otp_confirm_time(LocalDateTime.now());
                mongoTemplate.save(match);
                return true;
            }
        } catch (NumberFormatException e) {
            return false;
        }
        return false;
    }

    public String getPhoneNumberBySession(String sessionId) {
        mobile_otp entry = mongoTemplate.findById(sessionId, mobile_otp.class);
        return entry != null ? entry.getMobile_otp_number() : null;
    }
}
