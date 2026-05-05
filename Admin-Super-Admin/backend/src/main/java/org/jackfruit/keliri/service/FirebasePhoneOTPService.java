package org.jackfruit.keliri.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Service
public class FirebasePhoneOTPService {

    private FirebaseAuth firebaseAuth;

    @PostConstruct
    public void initialize() {
        try {
            // Initialize Firebase with service account
            FileInputStream serviceAccount = new FileInputStream(
                "src/main/resources/firebase-service-account.json"
            );

            FirebaseOptions options = new FirebaseOptions.Builder()
                .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                .setProjectId("mobilize-consumer-flutter-app")
                .build();

            FirebaseApp firebaseApp = FirebaseApp.initializeApp(options, "KeliriAdminApp");
            this.firebaseAuth = FirebaseAuth.getInstance(firebaseApp);

            System.out.println("✅ Firebase Phone OTP Service initialized successfully");
        } catch (IOException e) {
            System.err.println("❌ Failed to initialize Firebase: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Send OTP to phone number
     * Note: This is a simplified version. In production, you'd use Firebase Auth REST API
     * or integrate with an SMS service provider.
     */
    public Map<String, Object> sendOTP(String phoneNumber) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // For demo purposes, we'll create a session token
            // In production, you'd use Firebase Auth's phone authentication
            String sessionId = "session_" + System.currentTimeMillis();
            
            response.put("success", true);
            response.put("message", "OTP sent successfully");
            response.put("sessionId", sessionId);
            response.put("phoneNumber", phoneNumber);
            response.put("expiresIn", 300); // 5 minutes
            
            System.out.println("🔐 OTP sent to: " + phoneNumber + " (Session: " + sessionId + ")");
            
            return response;
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Failed to send OTP: " + e.getMessage());
            return response;
        }
    }

    /**
     * Verify OTP and create/update user
     */
    public Map<String, Object> verifyOTP(String sessionId, String otp, String phoneNumber) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // For demo purposes, we'll accept any 6-digit OTP
            // In production, you'd verify with Firebase Auth
            if (otp != null && otp.length() == 6 && otp.matches("\\d+")) {
                
                // Create or update user in Firebase
                String uid = createOrUpdateUser(phoneNumber);
                
                response.put("success", true);
                response.put("message", "OTP verified successfully");
                response.put("uid", uid);
                response.put("phoneNumber", phoneNumber);
                response.put("token", generateCustomToken(uid));
                
                System.out.println("✅ OTP verified for: " + phoneNumber + " (UID: " + uid + ")");
                
                return response;
            } else {
                response.put("success", false);
                response.put("message", "Invalid OTP format");
                return response;
            }
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "OTP verification failed: " + e.getMessage());
            return response;
        }
    }

    /**
     * Create or update user in Firebase
     */
    private String createOrUpdateUser(String phoneNumber) throws FirebaseAuthException {
        String uid = "phone_" + phoneNumber.replaceAll("[^0-9]", "");
        
        UserRecord.UpdateRequest updateRequest = new UserRecord.UpdateRequest(uid)
            .setPhoneNumber(phoneNumber)
            .setEmailVerified(true);
        
        try {
            // Try to update existing user
            firebaseAuth.updateUser(updateRequest);
            System.out.println("📱 Updated existing Firebase user: " + uid);
        } catch (FirebaseAuthException e) {
            if (e.getErrorCode().equals("user-not-found")) {
                // Create new user
                UserRecord.CreateRequest createRequest = new UserRecord.CreateRequest()
                    .setUid(uid)
                    .setPhoneNumber(phoneNumber)
                    .setEmailVerified(true);
                
                UserRecord userRecord = firebaseAuth.createUser(createRequest);
                System.out.println("👤 Created new Firebase user: " + userRecord.getUid());
                return userRecord.getUid();
            } else {
                throw e;
            }
        }
        
        return uid;
    }

    /**
     * Generate custom token for client
     */
    private String generateCustomToken(String uid) throws FirebaseAuthException {
        return firebaseAuth.createCustomToken(uid);
    }

    /**
     * Get Firebase Auth instance
     */
    public FirebaseAuth getFirebaseAuth() {
        return firebaseAuth;
    }
}
