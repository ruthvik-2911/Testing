package org.jackfruit.keliri.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.util.ByteArrayDataSource;
import java.util.logging.Logger;

@Service
public class EmailService {
    private static final Logger logger = Logger.getLogger(EmailService.class.getName());

    private final JavaMailSender javaMailSender;

    @Value("${app.mail.from:sonuayadavsk@gmail.com}")
    private String fromEmail;

    @Value("${app.mail.from-name:Keliri Platform}")
    private String fromName;

    @Value("${app.superadmin.portal-url:http://localhost:5175}")
    private String portalUrl;

    public EmailService(JavaMailSender javaMailSender) {
        this.javaMailSender = javaMailSender;
    }

    /**
     * Send a welcome email to newly created sub-admin with login instructions
     * and one-time password setup link
     */
    public void sendSubAdminWelcomeEmail(String recipientEmail, String subAdminName, String setupToken,
            String temporaryPassword) {
        try {
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setFrom(fromEmail, fromName);
            helper.setTo(recipientEmail);
            helper.setSubject("Welcome to Keliri Admin Platform - Setup Your Password");

            String setupLink = portalUrl + "/setup-password?token=" + setupToken;
            String htmlContent = buildWelcomeEmailHtml(subAdminName, recipientEmail, temporaryPassword, setupLink);

            helper.setText(htmlContent, true);

            javaMailSender.send(mimeMessage);
            logger.info("Welcome email sent successfully to: " + recipientEmail);
        } catch (MessagingException | java.io.UnsupportedEncodingException e) {
            logger.severe("Failed to send welcome email to " + recipientEmail + ": " + e.getMessage());
            throw new RuntimeException("Failed to send welcome email", e);
        }
    }

    /**
     * Send password reset email to sub-admin
     */
    public void sendPasswordResetEmail(String recipientEmail, String subAdminName, String resetToken) {
        try {
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setFrom(fromEmail, fromName);
            helper.setTo(recipientEmail);
            helper.setSubject("Password Reset Request - Keliri Admin Platform");

            String resetLink = portalUrl + "/reset-password?token=" + resetToken;
            String htmlContent = buildPasswordResetEmailHtml(subAdminName, resetLink);

            helper.setText(htmlContent, true);

            javaMailSender.send(mimeMessage);
            logger.info("Password reset email sent to: " + recipientEmail);
        } catch (MessagingException | java.io.UnsupportedEncodingException e) {
            logger.severe("Failed to send password reset email to " + recipientEmail + ": " + e.getMessage());
            throw new RuntimeException("Failed to send password reset email", e);
        }
    }

    /**
     * Send account locked notification email
     */
    public void sendAccountLockedEmail(String recipientEmail, String subAdminName, String unlockToken,
            String resetToken) {
        try {
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setFrom(fromEmail, fromName);
            helper.setTo(recipientEmail);
            helper.setSubject("Account Locked - Keliri Admin Platform");

            String unlockLink = portalUrl + "/unlock-account?token=" + unlockToken;
            String resetLink = portalUrl + "/reset-password?token=" + resetToken;
            String htmlContent = buildAccountLockedEmailHtml(subAdminName, unlockLink, resetLink);

            helper.setText(htmlContent, true);

            javaMailSender.send(mimeMessage);
            logger.info("Account locked notification sent to: " + recipientEmail);
        } catch (MessagingException | java.io.UnsupportedEncodingException e) {
            logger.severe("Failed to send account locked email to " + recipientEmail + ": " + e.getMessage());
            throw new RuntimeException("Failed to send account locked email", e);
        }
    }

    /**
     * Send simple text email
     */
    public void sendSimpleEmail(String to, String subject, String text) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            javaMailSender.send(message);
            logger.info("Simple email sent to: " + to);
        } catch (Exception e) {
            logger.severe("Failed to send email to " + to + ": " + e.getMessage());
            throw new RuntimeException("Failed to send email", e);
        }
    }

    /**
     * Send payment invoice email with PDF attachment
     */
    public void sendInvoiceEmail(String toEmail, String adminName, String adName, double amount, byte[] pdfInvoice) {
        try {
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setFrom(fromEmail, fromName);
            helper.setTo(toEmail);
            helper.setSubject("Payment Confirmation & Invoice - " + adName);

            String htmlContent = buildInvoiceEmailHtml(adminName, adName, amount);
            helper.setText(htmlContent, true);

            ByteArrayDataSource dataSource = new ByteArrayDataSource(pdfInvoice, "application/pdf");
            helper.addAttachment("Invoice_" + adName.replaceAll("[^a-zA-Z0-9.-]", "_") + ".pdf", dataSource);

            javaMailSender.send(mimeMessage);
            logger.info("Invoice email sent successfully to: " + toEmail);
        } catch (MessagingException | java.io.UnsupportedEncodingException e) {
            logger.severe("Failed to send invoice email to " + toEmail + ": " + e.getMessage());
            throw new RuntimeException("Failed to send invoice email", e);
        }
    }

    private String buildWelcomeEmailHtml(String name, String email, String temporaryPassword, String setupLink) {
        return "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "    <style>" +
                "        body { font-family: Arial, sans-serif; color: #333; }" +
                "        .container { max-width: 600px; margin: 0 auto; padding: 20px; }" +
                "        .header { background-color: #2c3e50; color: white; padding: 20px; text-align: center; border-radius: 5px 5px 0 0; }"
                +
                "        .content { background-color: #f9f9f9; padding: 30px; border: 1px solid #ddd; }" +
                "        .footer { background-color: #ecf0f1; padding: 15px; text-align: center; border-radius: 0 0 5px 5px; font-size: 12px; color: #7f8c8d; }"
                +
                "        .button { background-color: #3498db; color: white; padding: 12px 30px; text-decoration: none; border-radius: 5px; display: inline-block; margin: 20px 0; }"
                +
                "        .button:hover { background-color: #2980b9; }" +
                "        .info-box { background-color: #e8f4f8; padding: 15px; border-left: 4px solid #3498db; margin: 15px 0; }"
                +
                "        .info-box strong { color: #2c3e50; }" +
                "        .credentials-box { background-color: #ecf8ff; padding: 15px; border: 2px solid #3498db; border-radius: 5px; margin: 15px 0; font-family: monospace; }"
                +
                "        .credentials-box p { margin: 8px 0; }" +
                "        .temp-pass { background-color: #fff9e6; padding: 10px; border-radius: 3px; font-weight: bold; color: #e74c3c; }"
                +
                "    </style>" +
                "</head>" +
                "<body>" +
                "    <div class=\"container\">" +
                "        <div class=\"header\">" +
                "            <h1>Welcome to Keliri Admin Platform</h1>" +
                "        </div>" +
                "        <div class=\"content\">" +
                "            <p>Hello <strong>" + (name != null ? name : "Admin") + "</strong>,</p>" +
                "            <p>Your sub-admin account has been successfully created on the Keliri Platform. Below are your login credentials and instructions:</p>"
                +
                "            " +
                "            <div class=\"credentials-box\">" +
                "                <p><strong>📧 Email:</strong> " + email + "</p>" +
                "                <p><strong>🔐 Temporary Password:</strong></p>" +
                "                <p class=\"temp-pass\">" + temporaryPassword + "</p>" +
                "                <p style=\"font-size: 12px; color: #7f8c8d; margin-top: 10px;\">You can use this password to login immediately, or click the link below to set your own secure password.</p>"
                +
                "            </div>" +
                "            " +
                "            <div style=\"display: flex; gap: 10px; justify-content: center; margin: 20px 0;\">" +
                "                <center>" +
                "                    <a href=\"" + portalUrl
                + "\" class=\"button\" style=\"background-color: #2ecc71;\">🚀 Login Directly</a>" +
                "                    <a href=\"" + setupLink + "\" class=\"button\">🔐 Set My Own Password</a>" +
                "                </center>" +
                "            </div>" +
                "            <p><small>If the buttons don't work, copy and paste this link to setup your password:<br>"
                +
                "                <code style=\"word-break: break-all;\">" + setupLink + "</code></small></p>" +
                "            " +
                "            <h3>📋 Next Steps:</h3>" +
                "            <ol>" +
                "                <li>Click the button above to set your secure password</li>" +
                "                <li>Use a strong password with at least 8 characters (mix of uppercase, lowercase, numbers, and symbols)</li>"
                +
                "                <li>Log in with your email and your new secure password</li>" +
                "                <li>Access the Keliri Admin Dashboard</li>" +
                "            </ol>" +
                "            " +
                "            <div class=\"info-box\">" +
                "                <strong>⏰ Important:</strong> The password setup link expires in 24 hours. If it expires, contact your Master Super Admin to request a new invitation."
                +
                "            </div>" +
                "            " +
                "            <div class=\"info-box\" style=\"background-color: #fff3cd; border-left-color: #f39c12;\">"
                +
                "                <strong>🔒 Security Reminder:</strong>" +
                "                <ul>" +
                "                    <li>Never share your credentials with anyone</li>" +
                "                    <li>Use a unique, strong password</li>" +
                "                    <li>Be cautious of phishing emails</li>" +
                "                    <li>Always verify URLs before entering credentials</li>" +
                "                </ul>" +
                "            </div>" +
                "            " +
                "            <p>If you have any questions or need assistance, please contact your Master Super Admin.</p>"
                +
                "            <p>Best regards,<br><strong>Keliri Admin Team</strong></p>" +
                "        </div>" +
                "        <div class=\"footer\">" +
                "            <p>&copy; 2026 Keliri Platform. All rights reserved.</p>" +
                "            <p>This is an automated message, please do not reply to this email.</p>" +
                "        </div>" +
                "    </div>" +
                "</body>" +
                "</html>";
    }

    private String buildPasswordResetEmailHtml(String name, String resetLink) {
        return "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "    <style>" +
                "        body { font-family: Arial, sans-serif; color: #333; }" +
                "        .container { max-width: 600px; margin: 0 auto; padding: 20px; }" +
                "        .header { background-color: #2c3e50; color: white; padding: 20px; text-align: center; border-radius: 5px 5px 0 0; }"
                +
                "        .content { background-color: #f9f9f9; padding: 30px; border: 1px solid #ddd; }" +
                "        .footer { background-color: #ecf0f1; padding: 15px; text-align: center; border-radius: 0 0 5px 5px; font-size: 12px; color: #7f8c8d; }"
                +
                "        .button { background-color: #e74c3c; color: white; padding: 12px 30px; text-decoration: none; border-radius: 5px; display: inline-block; margin: 20px 0; }"
                +
                "        .button:hover { background-color: #c0392b; }" +
                "        .info-box { background-color: #fef5e7; padding: 15px; border-left: 4px solid #f39c12; margin: 15px 0; }"
                +
                "    </style>" +
                "</head>" +
                "<body>" +
                "    <div class=\"container\">" +
                "        <div class=\"header\">" +
                "            <h1>Password Reset Request</h1>" +
                "        </div>" +
                "        <div class=\"content\">" +
                "            <p>Hello <strong>" + (name != null ? name : "Admin") + "</strong>,</p>" +
                "            <p>We received a request to reset your password for the Keliri Admin Platform. If you made this request, click the button below to set a new password:</p>"
                +
                "            <center>" +
                "                <a href=\"" + resetLink + "\" class=\"button\">Reset Password</a>" +
                "            </center>" +
                "            <p><small>If the button doesn't work, copy and paste this link in your browser:<br>" +
                "                <code>" + resetLink + "</code></small></p>" +
                "            <div class=\"info-box\">" +
                "                <strong>Important:</strong> This link expires in 1 hour. If you didn't request this, please ignore this email."
                +
                "            </div>" +
                "            <p>Best regards,<br><strong>Keliri Admin Team</strong></p>" +
                "        </div>" +
                "        <div class=\"footer\">" +
                "            <p>&copy; 2026 Keliri Platform. All rights reserved.</p>" +
                "        </div>" +
                "    </div>" +
                "</body>" +
                "</html>";
    }

    private String buildAccountLockedEmailHtml(String name, String unlockLink, String resetLink) {
        return "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "    <style>" +
                "        body { font-family: Arial, sans-serif; color: #333; }" +
                "        .container { max-width: 600px; margin: 0 auto; padding: 20px; }" +
                "        .header { background-color: #c0392b; color: white; padding: 20px; text-align: center; border-radius: 5px 5px 0 0; }"
                +
                "        .content { background-color: #f9f9f9; padding: 30px; border: 1px solid #ddd; }" +
                "        .footer { background-color: #ecf0f1; padding: 15px; text-align: center; border-radius: 0 0 5px 5px; font-size: 12px; color: #7f8c8d; }"
                +
                "        .warning-box { background-color: #fadbd8; padding: 15px; border-left: 4px solid #e74c3c; margin: 15px 0; }"
                +
                "        .button { background-color: #e74c3c; color: white; padding: 12px 30px; text-decoration: none; border-radius: 5px; display: inline-block; margin: 10px 0; font-weight: bold; }"
                +
                "        .button-secondary { background-color: #3498db; }" +
                "    </style>" +
                "</head>" +
                "<body>" +
                "    <div class=\"container\">" +
                "        <div class=\"header\">" +
                "            <h1>Account Security Alert</h1>" +
                "        </div>" +
                "        <div class=\"content\">" +
                "            <p>Hello <strong>" + (name != null ? name : "Admin") + "</strong>,</p>" +
                "            <div class=\"warning-box\">" +
                "                <strong>Your account has been temporarily locked</strong> due to multiple failed login attempts. This is a security measure to protect your account."
                +
                "            </div>" +
                "            <p>Your account will be automatically unlocked after 15 minutes. However, if you forgot your password or want to regain access immediately, use the options below:</p>"
                +
                "            <div style=\"text-align: center; margin: 20px 0;\">" +
                "                <a href=\"" + unlockLink
                + "\" class=\"button\">🔓 Unlock Account Only</a><br>" +
                "                <a href=\"" + resetLink
                + "\" class=\"button button-secondary\">🔐 Reset Password & Unlock</a>" +
                "            </div>" +
                "            <p><small>If you chose to reset your password, you will be able to set a new secure password and your account will be automatically unlocked.</small></p>"
                +
                "            <p>If you didn't attempt to login recently, please contact your Master Super Admin immediately.</p>"
                +
                "            <p>Best regards,<br><strong>Keliri Admin Team</strong></p>" +
                "        </div>" +
                "        <div class=\"footer\">" +
                "            <p>&copy; 2026 Keliri Platform. All rights reserved.</p>" +
                "        </div>" +
                "    </div>" +
                "</body>" +
                "</html>";
    }

    private String buildInvoiceEmailHtml(String name, String adName, double amount) {
        return "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <style>\n" +
                "        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; color: #333; background-color: #f4f5f7; margin: 0; padding: 40px 0; }\n"
                +
                "        .container { max-width: 520px; margin: 0 auto; background-color: #ffffff; border-radius: 8px; overflow: hidden; box-shadow: 0 4px 6px rgba(0, 0, 0, 0.05); }\n"
                +
                "        .header { background-color: #ffffff; padding: 30px; text-align: center; border-bottom: 1px solid #eaeaea; }\n"
                +
                "        .header img { height: 32px; }\n" +
                "        .content { padding: 40px 30px; }\n" +
                "        .title { font-size: 20px; font-weight: 600; color: #111827; margin-bottom: 24px; text-align: center; }\n"
                +
                "        .amount-box { background-color: #f9fafb; border: 1px solid #f3f4f6; border-radius: 8px; padding: 24px; text-align: center; margin-bottom: 30px; }\n"
                +
                "        .amount-label { font-size: 13px; color: #6b7280; text-transform: uppercase; letter-spacing: 0.05em; margin-bottom: 8px; }\n"
                +
                "        .amount-value { font-size: 32px; font-weight: 700; color: #111827; margin: 0; }\n" +
                "        .details { margin-bottom: 30px; font-size: 15px; line-height: 1.6; color: #4b5563; }\n" +
                "        .footer { background-color: #f9fafb; padding: 20px; text-align: center; border-top: 1px solid #eaeaea; font-size: 13px; color: #6b7280; }\n"
                +
                "        .footer-logo { font-weight: 700; color: #374151; margin-bottom: 8px; }\n" +
                "        .btn { display: inline-block; background-color: #004369; color: #ffffff; text-decoration: none; padding: 12px 24px; border-radius: 6px; font-weight: 600; font-size: 14px; margin-top: 20px; }\n"
                +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background-color: #f4f5f7; margin: 0; padding: 40px 0;\">\n"
                +
                "        <tr>\n" +
                "            <td align=\"center\">\n" +
                "                <div class=\"container\">\n" +
                "                    <div class=\"header\">\n" +
                "                        <h1 style=\"margin: 0; font-size: 24px; color: #004369; font-weight: 900; letter-spacing: -0.5px;\">KELIRI</h1>\n"
                +
                "                    </div>\n" +
                "                    <div class=\"content\">\n" +
                "                        <div class=\"title\">Payment Confirmation</div>\n" +
                "                        <div class=\"amount-box\">\n" +
                "                            <div class=\"amount-label\">Amount Paid</div>\n" +
                "                            <div class=\"amount-value\">INR " + String.format("%.2f", amount)
                + "</div>\n" +
                "                        </div>\n" +
                "                        <div class=\"details\">\n" +
                "                            <p>Hi <strong>" + (name != null ? name : "Admin") + "</strong>,</p>\n" +
                "                            <p>Thank you for your payment. This email confirms that we have successfully processed your transaction for the advertisement campaign: <strong>"
                + adName + "</strong>.</p>\n" +
                "                            <p>Your ad campaign has been automatically marked as <strong>ACTIVE</strong> and is now running.</p>\n"
                +
                "                            <p>A detailed PDF invoice is attached to this email for your accounting records.</p>\n"
                +
                "                        </div>\n" +
                "                    </div>\n" +
                "                    <div class=\"footer\">\n" +
                "                        <div class=\"footer-logo\">Keliri Platform Services</div>\n" +
                "                        <div>123 Business Park, Tech Zone, Gurgaon, Haryana 122003</div>\n" +
                "                        <div style=\"margin-top: 10px;\">&copy; 2026 Keliri. All rights reserved.</div>\n"
                +
                "                    </div>\n" +
                "                </div>\n" +
                "            </td>\n" +
                "        </tr>\n" +
                "    </table>\n" +
                "</body>\n" +
                "</html>";
    }
}
