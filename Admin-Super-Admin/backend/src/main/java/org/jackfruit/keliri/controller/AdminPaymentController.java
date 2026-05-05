package org.jackfruit.keliri.controller;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import io.jsonwebtoken.Claims;
import org.jackfruit.keliri.model.PaymentTransaction;
import org.jackfruit.keliri.model.ad_campaigns;
import org.jackfruit.keliri.repository.PaymentTransactionRepository;
import org.jackfruit.keliri.repository.SuperAdminRepository;
import org.jackfruit.keliri.repository.ad_campaignsRepository;
import org.jackfruit.keliri.service.JwtService;
import org.jackfruit.keliri.service.EmailService;
import org.jackfruit.keliri.service.InvoiceService;
import org.jackfruit.keliri.model.SuperAdmin;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/admin/payments")
public class AdminPaymentController {

    @Autowired
    private RazorpayClient razorpayClient;

    @Autowired
    private PaymentTransactionRepository paymentRepo;

    @Autowired
    private ad_campaignsRepository campaignRepo;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private SuperAdminRepository superAdminRepo;

    @Autowired
    private org.jackfruit.keliri.repository.advertisementsRepository adsRepo;

    @Value("${razorpay.key.id}")
    private String keyId;

    @Value("${razorpay.key.secret}")
    private String keySecret;

    @Value("${razorpay.currency}")
    private String currency;

    @PostMapping("/create-order")
    public ResponseEntity<?> createOrder(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, Object> data) {
        try {
            String adminId = extractAdminId(authHeader);
            if (adminId == null)
                return unauthorized();

            String adId = (String) data.get("adId");
            double amount = Double.parseDouble(data.get("amount").toString());

            // amount is in INR, Razorpay expects paise
            int amountInPaise = (int) (amount * 100);

            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", amountInPaise);
            orderRequest.put("currency", currency);
            orderRequest.put("receipt", "txn_" + System.currentTimeMillis());

            Order order = razorpayClient.orders.create(orderRequest);

            // Save transaction as PENDING
            PaymentTransaction transaction = new PaymentTransaction();
            transaction.setAdminId(adminId);
            transaction.setAdId(adId);
            transaction.setAmount(amount);
            transaction.setCurrency(currency);
            transaction.setRazorpayOrderId(order.get("id"));
            transaction.setStatus("PENDING");
            transaction.setCreatedAt(Instant.now());
            transaction.setUpdatedAt(Instant.now());
            paymentRepo.save(transaction);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "id", order.get("id"),
                    "amount", order.get("amount"),
                    "currency", order.get("currency"),
                    "keyId", keyId));

        } catch (RazorpayException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "message", "Razorpay error: " + e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Internal server error"));
        }
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verifyPayment(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, String> data) {
        try {
            // No strict need for auth here if we verify via signature, but good for
            // tracking
            String adminId = extractAdminId(authHeader);

            String razorpayOrderId = data.get("razorpay_order_id");
            String razorpayPaymentId = data.get("razorpay_payment_id");
            String razorpaySignature = data.get("razorpay_signature");

            // Verify signature
            JSONObject options = new JSONObject();
            options.put("razorpay_order_id", razorpayOrderId);
            options.put("razorpay_payment_id", razorpayPaymentId);
            options.put("razorpay_signature", razorpaySignature);

            boolean isValid = Utils.verifyPaymentSignature(options, keySecret);

            if (isValid) {
                Optional<PaymentTransaction> optTxn = paymentRepo.findByRazorpayOrderId(razorpayOrderId);
                if (optTxn.isPresent()) {
                    PaymentTransaction txn = optTxn.get();
                    txn.setRazorpayPaymentId(razorpayPaymentId);
                    txn.setRazorpaySignature(razorpaySignature);
                    txn.setStatus("SUCCESS");
                    txn.setUpdatedAt(Instant.now());
                    paymentRepo.save(txn);

                    // Update all PENDING campaigns for this Ad to ACTIVE
                    // (Assuming the user just published an ad and it created a campaign)
                    // Note: If we have campaignId in the transaction, it's better.
                    // For now, let's update by adId or just leave it to the user.
                    // Actually, the ad_campaigns table has advertisementId.

                    // In a real scenario, we'd probably have the campaignId link.
                    // Let's at least mark campaigns as ACTIVE if they belong to this ad and were
                    // PENDING.
                    updateCampaignStatus(txn.getAdId());
                    updateAdPaymentStatus(txn.getAdId());

                    // Generate and Send Invoice
                    try {
                        if (txn.getAdminId() != null) {
                            Optional<SuperAdmin> adminOpt = superAdminRepo.findById(txn.getAdminId());
                            if (adminOpt.isPresent()) {
                                SuperAdmin admin = adminOpt.get();
                                String adminName = admin.getName() != null && !admin.getName().isEmpty()
                                        ? admin.getName()
                                        : "Admin";

                                byte[] invoicePdf = invoiceService.generatePdfInvoice(
                                        txn.getRazorpayPaymentId(),
                                        txn.getAdId(),
                                        txn.getAmount(),
                                        adminName);

                                if (admin.getEmail() != null) {
                                    emailService.sendInvoiceEmail(admin.getEmail(), adminName, txn.getAdId(),
                                            txn.getAmount(), invoicePdf);
                                }
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        System.err.println("Failed to generate or send invoice email.");
                    }
                }

                return ResponseEntity.ok(Map.of("success", true, "message", "Payment verified successfully"));
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("success", false, "message", "Invalid signature"));
            }

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Internal server error"));
        }
    }

    @GetMapping
    public ResponseEntity<?> getTransactions(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            String adminId = extractAdminId(authHeader);
            if (adminId == null)
                return unauthorized();

            java.util.List<PaymentTransaction> transactions = paymentRepo.findByAdminId(adminId);
            return ResponseEntity.ok(Map.of("success", true, "data", transactions));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Internal server error"));
        }
    }

    @GetMapping("/paid-ads")
    public ResponseEntity<?> getPaidAdIds(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            String adminId = extractAdminId(authHeader);
            if (adminId == null)
                return unauthorized();

            java.util.List<PaymentTransaction> successTxns = paymentRepo.findByAdminIdAndStatus(adminId, "SUCCESS");
            java.util.List<String> paidAdIds = successTxns.stream()
                    .map(PaymentTransaction::getAdId)
                    .filter(id -> id != null)
                    .distinct()
                    .collect(java.util.stream.Collectors.toList());

            return ResponseEntity.ok(Map.of("success", true, "paidAdIds", paidAdIds));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Internal server error"));
        }
    }

    private void updateAdPaymentStatus(String adId) {
        if (adId == null) return;
        try {
            Optional<org.jackfruit.keliri.model.advertisements> adOpt = adsRepo.findByUid(adId);
            if (adOpt.isPresent()) {
                org.jackfruit.keliri.model.advertisements ad = adOpt.get();
                ad.setPaymentStatus("Paid");
                adsRepo.save(ad);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateCampaignStatus(String adId) {
        if (adId == null)
            return;
        try {
            java.util.List<ad_campaigns> campaigns = campaignRepo.findByAdvertisementId(adId);
            for (ad_campaigns campaign : campaigns) {
                if ("PENDING".equalsIgnoreCase(campaign.getCompaignsStatus()) || "INACTIVE".equalsIgnoreCase(campaign.getCompaignsStatus())) {
                    campaign.setCompaignsStatus("ACTIVE");
                    campaignRepo.save(campaign);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String extractAdminId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer "))
            return null;
        try {
            Claims claims = jwtService.parseToken(authHeader.substring(7));
            return claims.get("userId", String.class);
        } catch (Exception e) {
            return null;
        }
    }

    private ResponseEntity<?> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("success", false, "message", "Unauthorized"));
    }
}
