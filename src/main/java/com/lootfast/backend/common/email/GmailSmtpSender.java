package com.lootfast.backend.common.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.mail.MailException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
class GmailSmtpSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(GmailSmtpSender.class);

    private final JavaMailSender mailSender;
    private final String from;

    GmailSmtpSender(JavaMailSender mailSender,
                    @Value("${spring.mail.username}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    @Async("emailExecutor")
    public void sendSignupOtp(String to, String otp) {
        sendOtp(
                to,
                otp,
                "LootFast — Mã xác thực đăng ký",
                "Xác thực tài khoản LootFast"
        );
    }

    @Override
    @Async("emailExecutor")
    public void sendPasswordResetOtp(String to, String otp) {
        sendOtp(
                to,
                otp,
                "LootFast — Mã đặt lại mật khẩu",
                "Đặt lại mật khẩu LootFast"
        );
    }

    private void sendOtp(String to, String otp, String subject, String heading) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(buildBody(otp, heading), true);
            mailSender.send(message);
            log.info("OTP email dispatched successfully");
        } catch (MessagingException | MailException e) {
            // Async — caller đã trả response rồi, chỉ log để monitor
            log.error("Failed to send OTP email", e);
        }
    }

    private String buildBody(String otp, String heading) {
        return """
                <div style="font-family:Arial,sans-serif;max-width:480px;margin:auto">
                  <h2>%s</h2>
                  <p>Mã xác thực của bạn:</p>
                  <h1 style="letter-spacing:8px;color:#e74c3c">%s</h1>
                  <p>Mã có hiệu lực trong <strong>5 phút</strong>.</p>
                  <p style="color:#888;font-size:12px">Nếu bạn không yêu cầu điều này, hãy bỏ qua email này.</p>
                </div>
                """.formatted(heading, otp);
    }
}
