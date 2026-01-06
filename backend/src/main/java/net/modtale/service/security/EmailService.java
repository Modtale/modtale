package net.modtale.service.security;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Async
    public void sendVerificationEmail(String to, String username, String token) {
        String subject = "Verify your Modtale account";
        String link = frontendUrl + "/verify?token=" + token;
        String logoUrl = "https://modtale.net/assets/favicon.svg";

        String htmlContent = "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "<meta charset=\"UTF-8\">" +
                "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">" +
                "</head>" +
                "<body style=\"margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #f4f4f5;\">" +
                "<table role=\"presentation\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\" style=\"background-color: #f4f4f5; padding: 40px 0;\">" +
                "<tr>" +
                "<td align=\"center\">" +
                "  <table role=\"presentation\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\" style=\"max-width: 480px; background-color: #ffffff; border-radius: 16px; overflow: hidden; box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1);\">" +
                "    <tr>" +
                "      <td style=\"padding: 40px 40px 20px 40px; text-align: center;\">" +
                "        <img src=\"" + logoUrl + "\" alt=\"Modtale\" width=\"64\" height=\"64\" style=\"display: inline-block; width: 64px; height: 64px; border-radius: 14px; background-color: #000000; padding: 4px;\" />" +
                "      </td>" +
                "    </tr>" +
                "    <tr>" +
                "      <td style=\"padding: 0 40px; text-align: center;\">" +
                "        <h1 style=\"margin: 0; font-size: 24px; font-weight: 800; color: #18181b; letter-spacing: -0.5px;\">Welcome, " + username + "!</h1>" +
                "        <p style=\"margin: 16px 0 0 0; font-size: 16px; line-height: 24px; color: #52525b;\">" +
                "          Thanks for joining the community. Please verify your email address to get started sharing and downloading." +
                "        </p>" +
                "      </td>" +
                "    </tr>" +
                "    <tr>" +
                "      <td style=\"padding: 32px 40px; text-align: center;\">" +
                "        <a href=\"" + link + "\" style=\"display: inline-block; background-color: #000000; color: #ffffff; font-size: 16px; font-weight: 600; text-decoration: none; padding: 12px 32px; border-radius: 12px; transition: background-color 0.2s;\">" +
                "          Verify Email" +
                "        </a>" +
                "      </td>" +
                "    </tr>" +
                "    <tr>" +
                "      <td style=\"padding: 0 40px 40px 40px; text-align: center;\">" +
                "        <p style=\"margin: 0; font-size: 12px; line-height: 18px; color: #a1a1aa;\">" +
                "          This link will expire in 24 hours. If you didn't create an account, you can safely ignore this email." +
                "        </p>" +
                "      </td>" +
                "    </tr>" +
                "  </table>" +
                "  <table role=\"presentation\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\" style=\"max-width: 480px;\">" +
                "    <tr>" +
                "      <td style=\"padding: 24px; text-align: center;\">" +
                "        <p style=\"margin: 0; font-size: 12px; color: #a1a1aa;\">&copy; 2025 Modtale. All rights reserved.</p>" +
                "      </td>" +
                "    </tr>" +
                "  </table>" +
                "</td>" +
                "</tr>" +
                "</table>" +
                "</body>" +
                "</html>";

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            helper.setFrom("Modtale <noreply@modtale.net>");

            mailSender.send(message);
        } catch (MessagingException e) {
            throw new RuntimeException("Failed to send email", e);
        }
    }

    @Async
    public void sendPasswordResetEmail(String to, String username, String token) {
        String subject = "Reset your Modtale password";
        String link = frontendUrl + "/reset-password?token=" + token;
        String logoUrl = "https://modtale.net/assets/favicon.svg";

        String htmlContent = "<!DOCTYPE html>" +
                "<html>" +
                "<head><meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"></head>" +
                "<body style=\"margin: 0; padding: 0; font-family: system-ui, -apple-system, sans-serif; background-color: #f4f4f5;\">" +
                "<table role=\"presentation\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\" style=\"background-color: #f4f4f5; padding: 40px 0;\">" +
                "<tr><td align=\"center\">" +
                "  <table role=\"presentation\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\" style=\"max-width: 480px; background-color: #ffffff; border-radius: 16px; overflow: hidden; box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1);\">" +
                "    <tr><td style=\"padding: 40px 40px 20px 40px; text-align: center;\">" +
                "        <img src=\"" + logoUrl + "\" alt=\"Modtale\" width=\"64\" height=\"64\" style=\"width: 64px; height: 64px; border-radius: 14px; background-color: #000; padding: 4px;\" />" +
                "    </td></tr>" +
                "    <tr><td style=\"padding: 0 40px; text-align: center;\">" +
                "        <h1 style=\"margin: 0; font-size: 24px; font-weight: 800; color: #18181b;\">Reset Password</h1>" +
                "        <p style=\"margin: 16px 0 0 0; font-size: 16px; line-height: 24px; color: #52525b;\">" +
                "          We received a request to reset your password for your Modtale account: <strong>" + username + "</strong>." +
                "        </p>" +
                "    </td></tr>" +
                "    <tr><td style=\"padding: 32px 40px; text-align: center;\">" +
                "        <a href=\"" + link + "\" style=\"display: inline-block; background-color: #000; color: #fff; font-size: 16px; font-weight: 600; text-decoration: none; padding: 12px 32px; border-radius: 12px;\">Reset Password</a>" +
                "    </td></tr>" +
                "    <tr><td style=\"padding: 0 40px 40px 40px; text-align: center;\">" +
                "        <p style=\"margin: 0; font-size: 12px; line-height: 18px; color: #a1a1aa;\">" +
                "          This link expires in 1 hour. If you didn't request this, you can safely ignore this email." +
                "        </p>" +
                "    </td></tr>" +
                "  </table>" +
                "</td></tr></table>" +
                "</body></html>";

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            helper.setFrom("Modtale <noreply@modtale.net>");
            mailSender.send(message);
        } catch (MessagingException e) {
            throw new RuntimeException("Failed to send email", e);
        }
    }
}