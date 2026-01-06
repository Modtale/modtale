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

    private static final String LOGO_URL = "https://modtale.net/assets/favicon.png";

    @Async
    public void sendVerificationEmail(String to, String username, String token) {
        String subject = "Verify your Modtale account";
        String link = frontendUrl + "/verify?token=" + token;

        String title = "Welcome to Modtale, " + username + "!";
        String body = "Thanks for joining the community. Please verify your email address to get started sharing and downloading content.";
        String buttonText = "Verify Account";

        String htmlContent = buildHtmlEmail(title, body, link, buttonText);
        sendEmail(to, subject, htmlContent);
    }

    @Async
    public void sendPasswordResetEmail(String to, String username, String token) {
        String subject = "Reset your Modtale password";
        String link = frontendUrl + "/reset-password?token=" + token;

        String title = "Reset Password";
        String body = "We received a request to reset your password for the account <strong>" + username + "</strong>. If you didn't make this request, you can safely ignore this email.";
        String buttonText = "Reset Password";

        String htmlContent = buildHtmlEmail(title, body, link, buttonText);
        sendEmail(to, subject, htmlContent);
    }

    private void sendEmail(String to, String subject, String htmlContent) {
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

    private String buildHtmlEmail(String title, String bodyText, String buttonLink, String buttonLabel) {
        String bgPage = "#f8fafc";
        String bgCard = "#ffffff";
        String border = "#e2e8f0";
        String textHead = "#0f172a";
        String textBody = "#475569";
        String btnBg = "#0f172a";
        String btnText = "#ffffff";

        return "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "<meta charset=\"UTF-8\">" +
                "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">" +
                "<title>" + title + "</title>" +
                "</head>" +
                "<body style=\"margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: " + bgPage + ";\">" +
                "<table role=\"presentation\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\" style=\"background-color: " + bgPage + "; padding: 40px 0;\">" +
                "<tr>" +
                "<td align=\"center\">" +
                "  <table role=\"presentation\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\" style=\"max-width: 480px; background-color: " + bgCard + "; border: 1px solid " + border + "; border-radius: 16px; overflow: hidden; box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05);\">" +
                "    <tr>" +
                "      <td style=\"padding: 40px 40px 24px 40px; text-align: center;\">" +
                "        <img src=\"" + LOGO_URL + "\" alt=\"Modtale\" width=\"56\" height=\"56\" style=\"display: inline-block; width: 56px; height: 56px; border-radius: 12px;\" />" +
                "      </td>" +
                "    </tr>" +
                "    <tr>" +
                "      <td style=\"padding: 0 40px; text-align: center;\">" +
                "        <h1 style=\"margin: 0; font-size: 24px; font-weight: 800; color: " + textHead + "; letter-spacing: -0.5px;\">" + title + "</h1>" +
                "      </td>" +
                "    </tr>" +
                "    <tr>" +
                "      <td style=\"padding: 16px 40px; text-align: center;\">" +
                "        <p style=\"margin: 0; font-size: 16px; line-height: 24px; color: " + textBody + ";\">" +
                "          " + bodyText +
                "        </p>" +
                "      </td>" +
                "    </tr>" +
                "    <tr>" +
                "      <td style=\"padding: 24px 40px 40px 40px; text-align: center;\">" +
                "        <a href=\"" + buttonLink + "\" target=\"_blank\" style=\"display: inline-block; background-color: " + btnBg + "; color: " + btnText + "; font-size: 16px; font-weight: 600; text-decoration: none; padding: 14px 32px; border-radius: 12px; transition: opacity 0.2s;\">" +
                "          " + buttonLabel +
                "        </a>" +
                "      </td>" +
                "    </tr>" +
                "  </table>" +
                "  <table role=\"presentation\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\" style=\"max-width: 480px;\">" +
                "    <tr>" +
                "      <td style=\"padding: 24px; text-align: center;\">" +
                "        <p style=\"margin: 0; font-size: 12px; color: #94a3b8;\">&copy; 2025 Modtale. All rights reserved.</p>" +
                "      </td>" +
                "    </tr>" +
                "  </table>" +
                "</td>" +
                "</tr>" +
                "</table>" +
                "</body>" +
                "</html>";
    }
}